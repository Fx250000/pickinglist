package com.industria.pickinglist.service;

import com.industria.pickinglist.model.HistoricoOp;
import com.industria.pickinglist.model.Movimentacao;
import com.industria.pickinglist.model.Pendencia;
import com.industria.pickinglist.model.PickingItem;
import com.industria.pickinglist.repository.HistoricoOpRepository;
import com.industria.pickinglist.repository.MovimentacaoRepository;
import com.industria.pickinglist.repository.PendenciaRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PickingService {

    @Autowired
    private MovimentacaoRepository repository;

    @Autowired
    private PendenciaRepository pendenciaRepository;

    @Autowired
    private HistoricoOpRepository historicoOpRepository;

    private List<PickingItem> listaAtual = new ArrayList<>();
    private String numeroOP = "---";

    // =================================================================================
    // 1. LEITURA DO FICHEIRO (Excel)
    // =================================================================================
    public void processarArquivo(MultipartFile file) throws Exception {
        listaAtual.clear();
        numeroOP = "---";

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = new HashMap<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new Exception("Arquivo vazio");

            for (Cell cell : headerRow) {
                colMap.put(cell.getStringCellValue().trim().toUpperCase(), cell.getColumnIndex());
            }

            if (!colMap.containsKey("COMPONENTE") && !colMap.containsKey("PRODUTO")) {
                throw new Exception("Coluna COMPONENTE/PRODUTO não encontrada.");
            }

            PickingItem currentParent = null;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Tenta capturar a OP
                if (numeroOP.equals("---")) {
                    String opCapturada = getCellValue(row, colMap, "NUMOPD", "OP", "ORDEM");
                    if (opCapturada != null && !opCapturada.isEmpty()) numeroOP = opCapturada;
                }

                String rawCode = getCellValue(row, colMap, "COMPONENTE", "PRODUTO");

                if (rawCode != null && !rawCode.isEmpty()) {
                    String cleanCode = rawCode.replace(".", "").replace("-", "").trim();
                    if (cleanCode.matches("\\d+") && cleanCode.length() < 13) {
                        cleanCode = String.format("%13s", cleanCode).replace(' ', '0');
                    }

                    String rawLoc = getCellValue(row, colMap, "LOCALESTOQUE", "LOCAL");
                    String localUpper = (rawLoc != null) ? rawLoc.toUpperCase() : "";

                    // Filtros de Exclusão (Matéria Prima, Laser, etc.)
                    if (cleanCode.startsWith("96") || localUpper.contains("LASER") || localUpper.contains("SERRA") || localUpper.contains("CHAPA") || localUpper.contains("3CH")) {
                        continue;
                    }

                    PickingItem item = new PickingItem();
                    item.setCodigo(formatarVisual(cleanCode));

                    // Lógica de Kits (Pai/Filho)
                    if (cleanCode.startsWith("02")) {
                        item.setPai(true);
                        currentParent = item;
                    } else {
                        item.setPai(false);
                        if (currentParent != null) {
                            item.setParentId(currentParent.getId());
                            item.setCodigoPai(currentParent.getCodigo());
                            currentParent.getChildrenIds().add(item.getId());
                        }
                    }

                    item.setDescricao(getCellValue(row, colMap, "DESCRICAO", "DESCRIÇÃO"));
                    item.setLocal(normalizarLocal(rawLoc));
                    item.setUnidade(getCellValue(row, colMap, "ABREVIATURA", "UN"));

                    String qtdStr = getCellValue(row, colMap, "QUANTIDADE", "QTD");
                    try {
                        if (qtdStr != null) item.setQtdRequerida(Double.parseDouble(qtdStr.replace(",", ".")));
                    } catch (Exception e) { item.setQtdRequerida(0.0); }

                    // Definição de Zona
                    String localNormUpper = item.getLocal().toUpperCase();
                    if (localNormUpper.startsWith("PN") || localNormUpper.contains("PALETE") || localNormUpper.equals("SEM LOC.") || localNormUpper.isEmpty()) {
                        item.setZona("3_CD");
                    } else {
                        item.setZona("2_ALMOXARIFADO");
                    }

                    listaAtual.add(item);
                }
            }
        }

        // Ordenação: Zona -> Local -> Código
        listaAtual.sort(Comparator.comparing(PickingItem::getZona)
                .thenComparing(PickingItem::getLocal)
                .thenComparing(PickingItem::getCodigo));
    }

    // =================================================================================
    // 2. OPERAÇÃO EM MEMÓRIA (Checklist)
    // =================================================================================

    public void atualizarQtd(String id, Double qtd) {
        listaAtual.stream().filter(i -> i.getId().equals(id)).findFirst().ifPresent(item -> {
            // Proteção contra valores nulos ou negativos
            double novaQtd = (qtd == null || qtd < 0) ? 0.0 : qtd;

            // Trava opcional: não separar mais do que o requerido
            if (novaQtd > item.getQtdRequerida()) {
                novaQtd = item.getQtdRequerida();
            }

            item.setQtdSeparada(novaQtd);
            item.atualizarStatus();
        });
    }

    public void baixarKitCompleto(String parentId) {
        PickingItem pai = listaAtual.stream().filter(i -> i.getId().equals(parentId)).findFirst().orElse(null);
        if (pai != null) {
            pai.setQtdSeparada(pai.getQtdRequerida());
            pai.atualizarStatus();

            List<PickingItem> filhos = listaAtual.stream().filter(i -> parentId.equals(i.getParentId())).collect(Collectors.toList());
            for (PickingItem filho : filhos) {
                filho.setQtdSeparada(filho.getQtdRequerida());
                filho.atualizarStatus();
            }
        }
    }

    // =================================================================================
    // 3. FINALIZAÇÃO (Gravação no Banco com Cálculo Correto)
    // =================================================================================

    @Transactional
    public void finalizarSeparacao(List<String> zonesPermitidas) {
        LocalDateTime agora = LocalDateTime.now();

        double totalRequerido = 0;
        double totalSeparado = 0;

        for (PickingItem item : listaAtual) {

            // A. FILTRO DE ZONA: Se houver filtro ativo, ignora itens de outras zonas
            // Isso impede que peças do Almoxarifado sejam marcadas como falta se só estamos conferindo o CD
            if (zonesPermitidas != null && !zonesPermitidas.isEmpty()) {
                if (!zonesPermitidas.contains(item.getZona())) {
                    continue;
                }
            }

            // Acumula totais para KPI (Ignora Kits Pais para não duplicar contagem)
            if (!item.isPai()) {
                totalRequerido += item.getQtdRequerida();
                totalSeparado += item.getQtdSeparada();
            }

            // B. GRAVA CONSUMO (O que foi efetivamente encontrado/separado)
            if (item.getQtdSeparada() > 0) {
                Movimentacao consumo = new Movimentacao(
                        this.numeroOP, item.getCodigo(), item.getDescricao(), item.getUnidade(),
                        item.getQtdSeparada(), "CONSUMO"
                );
                consumo.setDataMovimentacao(agora);
                repository.save(consumo);
            }

            // C. GRAVA FALTA e PENDÊNCIA (Cálculo Explícito: Requerido - Separado)
            // Usamos Math.round para evitar erros de ponto flutuante (ex: 0.00000001)
            double saldoReal = item.getQtdRequerida() - item.getQtdSeparada();
            saldoReal = Math.round(saldoReal * 1000.0) / 1000.0;

            if (!item.isPai() && saldoReal > 0) {

                // 1. Histórico Estático (Para gráficos Kanban)
                Movimentacao falta = new Movimentacao(
                        this.numeroOP, item.getCodigo(), item.getDescricao(), item.getUnidade(),
                        saldoReal, "FALTA" // <--- Grava a diferença
                );
                falta.setDataMovimentacao(agora);
                repository.save(falta);

                // 2. Pendência Operacional (Para Backlog de Entrega)
                Pendencia pendencia = new Pendencia();
                pendencia.setOp(this.numeroOP);
                pendencia.setCodigo(item.getCodigo());
                pendencia.setDescricao(item.getDescricao());
                pendencia.setUnidade(item.getUnidade());
                pendencia.setQuantidade(saldoReal); // <--- Grava a diferença
                pendencia.setDataAbertura(agora);
                pendencia.setStatus("ABERTO");
                pendencia.setTempoEspera("-");
                pendenciaRepository.save(pendencia);
            }
        }

        // D. GRAVA O RESUMO DA OP (KPI)
        HistoricoOp historico = new HistoricoOp();
        historico.setOp(this.numeroOP);
        historico.setDataFinalizacao(agora);
        historico.setTotalItens(totalRequerido);
        historico.setItensAtendidos(totalSeparado);

        if (totalRequerido > 0) {
            historico.setPercentualAtendimento((totalSeparado / totalRequerido) * 100.0);
        } else {
            historico.setPercentualAtendimento(100.0);
        }

        historicoOpRepository.save(historico);
    }

    // =================================================================================
    // 4. MÉTODOS DE GERENCIAMENTO DE BACKLOG (Busca e Exclusão)
    // =================================================================================

    // Busca Pendências (Todas ou Filtradas por código)
    public List<Pendencia> buscarPendenciasAbertas(String termoBusca) {
        if (termoBusca != null && !termoBusca.isEmpty()) {
            return pendenciaRepository.findByCodigoContainingAndStatus(termoBusca, "ABERTO");
        }
        return pendenciaRepository.findByStatus("ABERTO");
    }

    // Atalho para buscar todas
    public List<Pendencia> getPendenciasAbertas() {
        return buscarPendenciasAbertas(null);
    }

    // Excluir todas as pendências de uma OP (Caso de erro)
    @Transactional
    public void excluirPendenciasDaOp(String op) {
        pendenciaRepository.deleteByOp(op);
    }

    // Dar baixa em uma pendência individual
    public void realizarEntrega(Long pendenciaId) {
        pendenciaRepository.findById(pendenciaId).ifPresent(p -> {
            p.registrarEntrega();
            pendenciaRepository.save(p);
        });
    }

    public List<Pendencia> getHistoricoEntregas() {
        return pendenciaRepository.findByStatusOrderByDataEntregaDesc("ENTREGUE");
    }

    // =================================================================================
    // 5. GETTERS E UTILITÁRIOS
    // =================================================================================

    // Retorna apenas itens com saldo devedor positivo E dentro da zona filtrada
    public List<PickingItem> getFaltantes(List<String> zonesPermitidas) {
        return listaAtual.stream()
                .filter(i -> !i.isPai())
                .filter(i -> {
                    // Recalcula saldo para garantir precisão
                    double saldo = i.getQtdRequerida() - i.getQtdSeparada();
                    return saldo > 0.001;
                })
                .filter(i -> (zonesPermitidas == null || zonesPermitidas.isEmpty()) || zonesPermitidas.contains(i.getZona()))
                .collect(Collectors.toList());
    }

    // Sobrecarga sem filtro (para compatibilidade com chamadas antigas)
    public List<PickingItem> getFaltantes() {
        return getFaltantes(null);
    }

    public List<Movimentacao> getHistorico(LocalDateTime inicio, LocalDateTime fim) {
        return repository.buscarPorPeriodo(inicio, fim);
    }

    public List<HistoricoOp> getHistoricoOps() {
        return historicoOpRepository.findAllByOrderByDataFinalizacaoDesc();
    }

    public List<Movimentacao> getAnalise(LocalDateTime inicio, LocalDateTime fim, String tipo) {
        List<Movimentacao> todas = repository.buscarPorPeriodo(inicio, fim);
        return todas.stream().filter(m -> m.getTipo().equals(tipo)).collect(Collectors.toList());
    }

    // --- Utilitários de Lista e Excel ---
    public String getNumeroOP() { return numeroOP; }

    public List<PickingItem> getListaFiltrada(List<String> zonasPermitidas) {
        if (zonasPermitidas == null || zonasPermitidas.isEmpty()) return listaAtual;
        return listaAtual.stream().filter(item -> zonasPermitidas.contains(item.getZona())).collect(Collectors.toList());
    }

    public List<PickingItem> getListaCompleta() { return listaAtual; }

    private String normalizarLocal(String loc) {
        if (loc == null || loc.equals("0") || loc.equals("-") || loc.trim().isEmpty()) return "SEM LOC.";
        Pattern p = Pattern.compile("(.*?)(\\d+)$");
        Matcher m = p.matcher(loc.trim());
        if (m.matches()) return m.group(1) + String.format("%03d", Integer.parseInt(m.group(2)));
        return loc.trim();
    }

    private String getCellValue(Row row, Map<String, Integer> colMap, String... colNames) {
        for (String name : colNames) {
            if (colMap.containsKey(name)) {
                Cell cell = row.getCell(colMap.get(name));
                if (cell == null) return null;
                return switch (cell.getCellType()) {
                    case STRING -> cell.getStringCellValue().trim();
                    case NUMERIC -> (cell.getNumericCellValue() == (long) cell.getNumericCellValue()) ? String.format("%d", (long) cell.getNumericCellValue()) : String.valueOf(cell.getNumericCellValue());
                    default -> "";
                };
            }
        }
        return null;
    }

    private String formatarVisual(String c) {
        if (c != null && c.length() == 13) return c.substring(0,2) + "." + c.substring(2,5) + "." + c.substring(5,8) + "." + c.substring(8);
        return c;
    }
}
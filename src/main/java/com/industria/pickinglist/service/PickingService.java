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

    @Autowired private MovimentacaoRepository repository;
    @Autowired private PendenciaRepository pendenciaRepository;
    @Autowired private HistoricoOpRepository historicoOpRepository;

    // Lista em memória (visível entre threads, manipulação centralizada)
    private volatile List<PickingItem> listaAtual = new ArrayList<>();
    private String numeroOP = "---";

    // =================================================================================
    // 1. LEITURA DO ARQUIVO (Excel)
    // =================================================================================
    public void processarArquivo(MultipartFile file) throws Exception {
        // Usa lista local para evitar condições de corrida
        List<PickingItem> novaLista = new ArrayList<>();
        String novaOP = "---";

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = new HashMap<>();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new Exception("Arquivo vazio");
            }

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

                // Captura OP apenas uma vez
                if ("---".equals(novaOP)) {
                    String opCapturada = getCellValue(row, colMap, "NUMOPD", "OP", "ORDEM");
                    if (opCapturada != null && !opCapturada.isEmpty()) {
                        novaOP = opCapturada;
                    }
                }

                String rawCode = getCellValue(row, colMap, "COMPONENTE", "PRODUTO");
                if (rawCode == null || rawCode.isEmpty()) {
                    continue;
                }

                String cleanCode = rawCode.replace(".", "").replace("-", "").trim();
                if (cleanCode.matches("\\d+") && cleanCode.length() < 13) {
                    cleanCode = String.format("%13s", cleanCode).replace(' ', '0');
                }

                String rawLoc = getCellValue(row, colMap, "LOCALESTOQUE", "LOCAL");
                String localUpper = rawLoc != null ? rawLoc.toUpperCase() : "";

                // Filtros de exclusão
                if (cleanCode.startsWith("96")
                        || localUpper.contains("LASER")
                        || localUpper.contains("SERRA")
                        || localUpper.contains("CHAPA")
                        || localUpper.contains("3CH")) {
                    continue;
                }

                PickingItem item = new PickingItem();
                item.setCodigo(formatarVisual(cleanCode));

                // Kit pai x filho
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
                    if (qtdStr != null) {
                        item.setQtdRequerida(Double.parseDouble(qtdStr.replace(",", ".")));
                    } else {
                        item.setQtdRequerida(0.0);
                    }
                } catch (Exception e) {
                    item.setQtdRequerida(0.0);
                }

                // Zona
                String localNormUpper = item.getLocal().toUpperCase();
                if (localNormUpper.startsWith("PN")
                        || localNormUpper.contains("PALETE")
                        || localNormUpper.equals("SEM LOC.")
                        || localNormUpper.isEmpty()) {
                    item.setZona("3CD");
                } else {
                    item.setZona("2ALMOXARIFADO");
                }

                novaLista.add(item);
            }

            // Ordenação estável
            novaLista.sort(
                    Comparator.comparing(PickingItem::getZona, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(PickingItem::getLocal, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(PickingItem::getCodigo, Comparator.nullsLast(Comparator.naturalOrder()))
            );

            // Atualização atômica
            this.listaAtual = novaLista;
            this.numeroOP = novaOP;
        }
    }

    // =================================================================================
    // 2. OPERAÇÃO EM MEMÓRIA (Checklist)
    // =================================================================================
    public void atualizarQtd(String id, Double qtd) {
        PickingItem item = listaAtual.stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (item == null) return;

        double novaQtd = (qtd == null || qtd < 0) ? 0.0 : qtd;
        if (novaQtd > item.getQtdRequerida()) {
            novaQtd = item.getQtdRequerida();
        }

        item.setQtdSeparada(novaQtd);
        item.atualizarStatus();

        if (item.getParentId() != null) {
            verificarStatusKitPai(item.getParentId());
        }
    }

    private void verificarStatusKitPai(String parentId) {
        PickingItem pai = listaAtual.stream()
                .filter(i -> i.getId().equals(parentId))
                .findFirst()
                .orElse(null);

        if (pai == null) return;

        List<PickingItem> filhos = listaAtual.stream()
                .filter(i -> parentId.equals(i.getParentId()))
                .collect(Collectors.toList());

        boolean todosCompletos = filhos.stream().allMatch(f -> "OK".equals(f.getStatus()));
        if (todosCompletos) {
            pai.setStatus("OK");
            pai.setQtdSeparada(pai.getQtdRequerida());
        } else {
            boolean algumIniciado = filhos.stream().anyMatch(f -> f.getQtdSeparada() > 0);
            pai.setStatus(algumIniciado ? "PARCIAL" : "PENDENTE");
        }
    }

    public void baixarKitCompleto(String parentId) {
        PickingItem pai = listaAtual.stream()
                .filter(i -> i.getId().equals(parentId))
                .findFirst()
                .orElse(null);

        if (pai == null) return;

        pai.setQtdSeparada(pai.getQtdRequerida());
        pai.setStatus("OK");

        List<PickingItem> filhos = listaAtual.stream()
                .filter(i -> parentId.equals(i.getParentId()))
                .collect(Collectors.toList());

        for (PickingItem filho : filhos) {
            filho.setQtdSeparada(filho.getQtdRequerida());
            filho.setStatus("OK");
        }
    }

    // =================================================================================
    // 3. FINALIZAÇÃO: grava consumos, faltas, pendências e histórico OP
    // =================================================================================
    @Transactional
    public void finalizarSeparacao(List<String> zonesPermitidasIgnoradasNoSave) {
        LocalDateTime agora = LocalDateTime.now();
        double totalRequerido = 0.0;
        double totalSeparado = 0.0;

        for (PickingItem item : listaAtual) {
            // KPIs só para itens não-pai
            if (!item.isPai()) {
                totalRequerido += item.getQtdRequerida();
                totalSeparado += item.getQtdSeparada();
            }

            // 1) CONSUMO
            if (item.getQtdSeparada() > 0) {
                Movimentacao consumo = new Movimentacao(
                        this.numeroOP,
                        item.getCodigo(),
                        item.getDescricao(),
                        item.getUnidade(),
                        item.getQtdSeparada(),
                        "CONSUMO"
                );
                consumo.setDataMovimentacao(agora);
                repository.save(consumo);
            }

            // 2) FALTA + PENDÊNCIA
            double saldoReal = item.getQtdRequerida() - item.getQtdSeparada();
            saldoReal = Math.round(saldoReal * 1000.0) / 1000.0;

            if (!item.isPai() && saldoReal > 0.0) {
                Movimentacao falta = new Movimentacao(
                        this.numeroOP,
                        item.getCodigo(),
                        item.getDescricao(),
                        item.getUnidade(),
                        saldoReal,
                        "FALTA"
                );
                falta.setDataMovimentacao(agora);
                repository.save(falta);

                Pendencia pendencia = new Pendencia();
                pendencia.setOp(this.numeroOP);
                pendencia.setCodigo(item.getCodigo());
                pendencia.setDescricao(item.getDescricao());
                pendencia.setUnidade(item.getUnidade());
                pendencia.setQtdOriginal(item.getQtdRequerida());
                pendencia.setQuantidade(saldoReal);           // saldo devedor
                pendencia.setDataAbertura(agora);
                pendencia.setStatus("ABERTO");
                pendencia.setTempoEspera("-");                // calculado depois
                pendenciaRepository.save(pendencia);
            }
        }

        // 3) Histórico OP
        HistoricoOp historico = new HistoricoOp();
        historico.setOp(this.numeroOP);
        historico.setDataFinalizacao(agora);
        historico.setTotalItens(totalRequerido);
        historico.setItensAtendidos(totalSeparado);
        if (totalRequerido > 0.0) {
            historico.setPercentualAtendimento((totalSeparado / totalRequerido) * 100.0);
        } else {
            historico.setPercentualAtendimento(100.0);
        }
        historicoOpRepository.save(historico);

        // Limpa estado em memória
        this.listaAtual = new ArrayList<>();
        this.numeroOP = "---";
    }

    // =================================================================================
    // 4. Pendências (Backlog)
    // =================================================================================
    public List<Pendencia> buscarPendenciasAbertas(String termoBusca) {
        if (termoBusca != null && !termoBusca.isEmpty()) {
            return pendenciaRepository.findByCodigoContainingAndStatus(termoBusca, "ABERTO");
        }
        return pendenciaRepository.findByStatus("ABERTO");
    }

    public List<Pendencia> getPendenciasAbertas() {
        return buscarPendenciasAbertas(null);
    }

    @Transactional
    public void excluirPendenciasDaOp(String op) {
        pendenciaRepository.deleteByOp(op);
    }

    @Transactional
    public void realizarEntrega(Long pendenciaId, Double qtdEntregue) {
        pendenciaRepository.findById(pendenciaId).ifPresent(p -> {
            p.registrarEntrega(qtdEntregue);  // COM quantidade
            pendenciaRepository.save(p);
        });
    }

    public List<Pendencia> getHistoricoEntregas() {
        return pendenciaRepository.findByStatusOrderByDataEntregaDesc("ENTREGUE");
    }

    // =================================================================================
    // 5. Histórico / Análises (Dashboard & Relatório OPs)
    // =================================================================================
    public List<Movimentacao> getHistorico(LocalDateTime inicio, LocalDateTime fim) {
        return repository.buscarPorPeriodo(inicio, fim);
    }

    public List<Movimentacao> getAnalise(LocalDateTime inicio, LocalDateTime fim, String tipo) {
        List<Movimentacao> todas = repository.buscarPorPeriodo(inicio, fim);
        return todas.stream()
                .filter(m -> tipo.equals(m.getTipo()))
                .collect(Collectors.toList());
    }

    public List<HistoricoOp> getHistoricoOps() {
        return historicoOpRepository.findAllByOrderByDataFinalizacaoDesc();
    }

// =================================================================================
// 6. Getters usados na UI
// =================================================================================

    public List<PickingItem> getFaltantes(List<String> zonesPermitidas) {
        return listaAtual.stream()
                .filter(i -> !i.isPai())
                .filter(i -> (i.getQtdRequerida() - i.getQtdSeparada()) > 0.001)
                // Aplica filtro visual APENAS no relatório
                .filter(i -> (zonesPermitidas == null || zonesPermitidas.isEmpty())
                        || zonesPermitidas.contains(i.getZona()))
                .collect(Collectors.toList());
    }

    public List<PickingItem> getFaltantes() {
        return getFaltantes(null);
    }

    public List<PickingItem> getListaFiltrada(List<String> zonesPermitidas) {
        if (zonesPermitidas == null || zonesPermitidas.isEmpty()) {
            return listaAtual;
        }
        return listaAtual.stream()
                .filter(item -> zonesPermitidas.contains(item.getZona()))
                .collect(Collectors.toList());
    }

    public List<PickingItem> getListaCompleta() {
        return listaAtual;
    }

    public String getNumeroOP() {
        return numeroOP;
    }

    // =================================================================================
    // 7. Utilitários
    // =================================================================================
    private String normalizarLocal(String loc) {
        if (loc == null || loc.equals("0") || loc.equals("-") || loc.trim().isEmpty()) {
            return "SEM LOC.";
        }
        // Mantém eventual prefixo e normaliza o número final com 3 dígitos
        Pattern p = Pattern.compile("(.*?)(\\d+)$");
        Matcher m = p.matcher(loc.trim());
        if (m.matches()) {
            return m.group(1) + String.format("%03d", Integer.parseInt(m.group(2)));
        }
        return loc.trim();
    }

    private String getCellValue(Row row, Map<String, Integer> colMap, String... colNames) {
        for (String name : colNames) {
            if (colMap.containsKey(name)) {
                Cell cell = row.getCell(colMap.get(name));
                if (cell == null) return null;
                return switch (cell.getCellType()) {
                    case STRING -> cell.getStringCellValue().trim();
                    case NUMERIC -> {
                        double v = cell.getNumericCellValue();
                        if (v == (long) v) {
                            yield String.format("%d", (long) v);
                        } else {
                            yield String.valueOf(v);
                        }
                    }
                    default -> null;
                };
            }
        }
        return null;
    }

    private String formatarVisual(String c) {
        if (c != null && c.length() == 13) {
            return c.substring(0, 2) + "." +
                    c.substring(2, 5) + "." +
                    c.substring(5, 8) + "." +
                    c.substring(8);
        }
        return c;
    }
}

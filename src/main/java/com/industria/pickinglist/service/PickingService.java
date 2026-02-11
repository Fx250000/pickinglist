package com.industria.pickinglist.service;

import com.industria.pickinglist.model.*;
import com.industria.pickinglist.repository.HistoricoOpRepository;
import com.industria.pickinglist.repository.MovimentacaoRepository;
import com.industria.pickinglist.repository.PendenciaRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    private volatile List<PickingItem> listaAtual = new ArrayList<>();
    private String numeroOP = "---";

    // =================================================================================
    // 1. PROCESSAMENTO DE ARQUIVOS (DISPATCHER)
    // =================================================================================
    public void processarArquivo(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();

        if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
            processarCsv(file);
        } else {
            processarExcel(file);
        }
    }

    // --- PARSER EXCEL (.xlsx) ---
    private void processarExcel(MultipartFile file) throws Exception {
        List<PickingItem> novaLista = new ArrayList<>();
        String novaOP = "---";

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = new HashMap<>();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new Exception("Arquivo Excel vazio");

            for (Cell cell : headerRow) {
                colMap.put(cell.getStringCellValue().trim().toUpperCase(), cell.getColumnIndex());
            }

            validateHeaders(colMap.keySet());

            PickingItem currentParent = null;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Extrair dados brutos
                String rawOp = getExcelValue(row, colMap, "NUMOPD", "OP", "ORDEM");
                String rawCode = getExcelValue(row, colMap, "COMPONENTE", "PRODUTO");
                String rawLoc = getExcelValue(row, colMap, "LOCALESTOQUE", "LOCAL");
                String rawDesc = getExcelValue(row, colMap, "DESCRICAO", "DESCRIÇÃO");
                String rawUn = getExcelValue(row, colMap, "ABREVIATURA", "UN");
                String rawQtd = getExcelValue(row, colMap, "QUANTIDADE", "QTD");

                if (novaOP.equals("---") && rawOp != null && !rawOp.isEmpty()) novaOP = rawOp;
                if (rawCode == null || rawCode.isEmpty()) continue;

                PickingItem item = criarItem(rawCode, rawLoc, rawDesc, rawUn, rawQtd, currentParent);

                if (item != null) {
                    if (item.isPai()) currentParent = item;
                    novaLista.add(item);
                }
            }
            finalizarProcessamento(novaLista, novaOP);
        }
    }

    // --- PARSER CSV (.csv) ---
    private void processarCsv(MultipartFile file) throws Exception {
        List<PickingItem> novaLista = new ArrayList<>();
        String novaOP = "---";

        // Tenta detectar delimitador comum (Brasil usa ';', EUA usa ',')
        // Usa ISO_8859_1 para garantir leitura correta de acentos (comum em exports Excel)
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.ISO_8859_1));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .withDelimiter(';'))) { // ASSUMINDO PONTO-E-VÍRGULA (Padrão BR)

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            // Normaliza headers para Uppercase para busca
            Map<String, String> normalizedHeaders = new HashMap<>();
            headerMap.forEach((k, v) -> normalizedHeaders.put(k.toUpperCase(), k));

            validateHeaders(normalizedHeaders.keySet());

            PickingItem currentParent = null;

            for (CSVRecord record : csvParser) {
                String rawOp = getCsvValue(record, normalizedHeaders, "NUMOPD", "OP", "ORDEM");
                String rawCode = getCsvValue(record, normalizedHeaders, "COMPONENTE", "PRODUTO");
                String rawLoc = getCsvValue(record, normalizedHeaders, "LOCALESTOQUE", "LOCAL");
                String rawDesc = getCsvValue(record, normalizedHeaders, "DESCRICAO", "DESCRIÇÃO");
                String rawUn = getCsvValue(record, normalizedHeaders, "ABREVIATURA", "UN");
                String rawQtd = getCsvValue(record, normalizedHeaders, "QUANTIDADE", "QTD");

                if (novaOP.equals("---") && rawOp != null && !rawOp.isEmpty()) novaOP = rawOp;
                if (rawCode == null || rawCode.isEmpty()) continue;

                PickingItem item = criarItem(rawCode, rawLoc, rawDesc, rawUn, rawQtd, currentParent);

                if (item != null) {
                    if (item.isPai()) currentParent = item;
                    novaLista.add(item);
                }
            }
            finalizarProcessamento(novaLista, novaOP);
        }
    }

    // =================================================================================
    // 2. LÓGICA UNIFICADA DE CRIAÇÃO (CORE)
    // =================================================================================
    private PickingItem criarItem(String rawCode, String rawLoc, String desc, String un, String qtdStr, PickingItem currentParent) {
        String cleanCode = rawCode.replace(".", "").replace("-", "").trim();
        if (cleanCode.matches("\\d+") && cleanCode.length() < 13) {
            cleanCode = String.format("%13s", cleanCode).replace(' ', '0');
        }

        String localUpper = rawLoc != null ? rawLoc.toUpperCase() : "";

        // Filtros de exclusão (Business Logic)
        if (cleanCode.startsWith("96") || localUpper.contains("LASER")
                || localUpper.contains("SERRA") || localUpper.contains("CHAPA")
                || localUpper.contains("3CH")) {
            return null;
        }

        PickingItem item = new PickingItem();
        item.setCodigo(formatarVisual(cleanCode));

        // Lógica Pai/Filho
        if (cleanCode.startsWith("02")) {
            item.setPai(true);
        } else {
            item.setPai(false);
            if (currentParent != null) {
                item.setParentId(currentParent.getId());
                item.setCodigoPai(currentParent.getCodigo());
                currentParent.getChildrenIds().add(item.getId());
            }
        }

        item.setDescricao(desc);
        item.setLocal(normalizarLocal(rawLoc));
        item.setUnidade(un);

        try {
            item.setQtdRequerida(qtdStr != null ? Double.parseDouble(qtdStr.replace(",", ".")) : 0.0);
        } catch (Exception e) {
            item.setQtdRequerida(0.0);
        }

        // Definição de Zona
        String localNormUpper = item.getLocal().toUpperCase();
        if (localNormUpper.startsWith("PN") || localNormUpper.contains("PALETE")
                || localNormUpper.equals("SEM LOC.") || localNormUpper.isEmpty()) {
            item.setZona("3CD");
        } else {
            item.setZona("2ALMOXARIFADO");
        }

        return item;
    }

    private void finalizarProcessamento(List<PickingItem> novaLista, String novaOP) {
        novaLista.sort(Comparator.comparing(PickingItem::getZona, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PickingItem::getLocal, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PickingItem::getCodigo, Comparator.nullsLast(Comparator.naturalOrder())));

        this.listaAtual = novaLista;
        this.numeroOP = novaOP;
    }

    private void validateHeaders(Set<String> headers) throws Exception {
        if (!headers.contains("COMPONENTE") && !headers.contains("PRODUTO")) {
            throw new Exception("Coluna OBRIGATÓRIA não encontrada: COMPONENTE ou PRODUTO");
        }
    }

    // =================================================================================
    // 3. HELPERS DE LEITURA
    // =================================================================================
    private String getExcelValue(Row row, Map<String, Integer> colMap, String... colNames) {
        for (String name : colNames) {
            if (colMap.containsKey(name)) {
                Cell cell = row.getCell(colMap.get(name));
                if (cell == null) return null;
                if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
                return cell.getStringCellValue().trim();
            }
        }
        return null;
    }

    private String getCsvValue(CSVRecord record, Map<String, String> normalizedHeaders, String... colNames) {
        for (String name : colNames) {
            if (normalizedHeaders.containsKey(name)) {
                String realHeader = normalizedHeaders.get(name);
                if (record.isMapped(realHeader)) {
                    return record.get(realHeader).trim();
                }
            }
        }
        return null;
    }

    // =================================================================================
    // 4. MÉTODOS EXISTENTES (Mantidos iguais)
    // =================================================================================
    public void atualizarQtd(String id, Double qtd) {
        PickingItem item = listaAtual.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
        if (item == null) return;
        double novaQtd = (qtd == null || qtd < 0) ? 0.0 : qtd;
        if (novaQtd > item.getQtdRequerida()) novaQtd = item.getQtdRequerida();
        item.setQtdSeparada(novaQtd);
        item.atualizarStatus();
    }

    @Transactional
    public void salvarBacklog(List<BacklogDTO> items) {
        LocalDateTime agora = LocalDateTime.now();
        double totalRequerido = 0.0;
        double totalSeparado = 0.0;

        for (BacklogDTO dto : items) {
            PickingItem original = listaAtual.stream()
                    .filter(i -> i.getId().equals(dto.getProductId()))
                    .findFirst()
                    .orElse(null);

            if (original != null) {
                double req = dto.getQuantityRequested();
                double found = dto.getQuantityFound();
                double missing = dto.getQuantityMissing();

                totalRequerido += req;
                totalSeparado += found;

                if (found > 0) {
                    Movimentacao consumo = new Movimentacao(this.numeroOP, original.getCodigo(),
                            original.getDescricao(), original.getUnidade(), found, "CONSUMO");
                    consumo.setDataMovimentacao(agora);
                    repository.save(consumo);
                }

                if (missing > 0) {
                    Movimentacao falta = new Movimentacao(this.numeroOP, original.getCodigo(),
                            original.getDescricao(), original.getUnidade(), missing, "FALTA");
                    falta.setDataMovimentacao(agora);
                    repository.save(falta);

                    Pendencia pendencia = new Pendencia();
                    pendencia.setOp(this.numeroOP);
                    pendencia.setCodigo(original.getCodigo());
                    pendencia.setDescricao(original.getDescricao());
                    pendencia.setUnidade(original.getUnidade());
                    pendencia.setQtdOriginal(req);
                    pendencia.setQuantidade(missing);
                    pendencia.setDataAbertura(agora);
                    pendencia.setStatus("ABERTO");
                    pendencia.setTempoEspera("-");
                    pendenciaRepository.save(pendencia);
                }
            }
        }

        HistoricoOp historico = new HistoricoOp();
        historico.setOp(this.numeroOP);
        historico.setDataFinalizacao(agora);
        historico.setTotalItens(totalRequerido);
        historico.setItensAtendidos(totalSeparado);
        historico.setPercentualAtendimento(totalRequerido > 0 ? (totalSeparado / totalRequerido) * 100.0 : 100.0);
        historicoOpRepository.save(historico);

        this.listaAtual = new ArrayList<>();
        this.numeroOP = "---";
    }

    // Compatibilidade
    @Transactional
    public void finalizarSeparacao(List<String> zones) {
        List<BacklogDTO> dtos = listaAtual.stream().map(i -> new BacklogDTO(
                i.getId(), i.getQtdRequerida(), i.getQtdSeparada(), i.getQtdRequerida() - i.getQtdSeparada()
        )).collect(Collectors.toList());
        salvarBacklog(dtos);
    }

    public long getContagemOpsFinalizadas() { return historicoOpRepository.count(); }
    public long getContagemOpsTotal() { return (!listaAtual.isEmpty()) ? historicoOpRepository.count() + 1 : historicoOpRepository.count(); }

    public List<String> getAlertasPendencia() {
        return pendenciaRepository.findByStatus("ABERTO").stream()
                .collect(Collectors.groupingBy(Pendencia::getOp, Collectors.counting()))
                .entrySet().stream()
                .map(e -> "OP " + e.getKey() + ": " + e.getValue() + " itens pendentes")
                .collect(Collectors.toList());
    }

    // Getters
    public List<Pendencia> buscarPendenciasAbertas(String termoBusca) {
        return (termoBusca != null && !termoBusca.isEmpty())
                ? pendenciaRepository.findByCodigoContainingAndStatus(termoBusca, "ABERTO")
                : pendenciaRepository.findByStatus("ABERTO");
    }

    public List<Pendencia> getHistoricoEntregas() { return pendenciaRepository.findByStatusOrderByDataEntregaDesc("ENTREGUE"); }

    public List<HistoricoOp> getHistoricoOps() {
        return historicoOpRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "dataFinalizacao"));
    }

    @Transactional
    public void excluirPendenciasDaOp(String op) { pendenciaRepository.deleteByOp(op); }

    @Transactional
    public void realizarEntrega(Long id, Double qtd) {
        pendenciaRepository.findById(id).ifPresent(p -> {
            p.registrarEntrega(qtd);
            pendenciaRepository.save(p);
        });
    }

    public List<Movimentacao> getAnalise(LocalDateTime i, LocalDateTime f, String t) {
        return repository.buscarPorPeriodo(i, f).stream().filter(m -> t.equals(m.getTipo())).collect(Collectors.toList());
    }

    public List<PickingItem> getFaltantes(List<String> z) {
        return listaAtual.stream().filter(i -> !i.isPai() && (i.getQtdRequerida() - i.getQtdSeparada()) > 0.001)
                .filter(i -> z == null || z.isEmpty() || z.contains(i.getZona())).collect(Collectors.toList());
    }

    public List<PickingItem> getListaFiltrada(List<String> z) {
        return (z == null || z.isEmpty()) ? listaAtual : listaAtual.stream().filter(i -> z.contains(i.getZona())).collect(Collectors.toList());
    }

    public String getNumeroOP() { return numeroOP; }

    private String normalizarLocal(String loc) {
        if (loc == null || loc.trim().isEmpty() || loc.equals("0")) return "SEM LOC.";
        Pattern p = Pattern.compile("(.*?)(\\d+)$");
        Matcher m = p.matcher(loc.trim());
        return m.matches() ? m.group(1) + String.format("%03d", Integer.parseInt(m.group(2))) : loc.trim();
    }

    private String formatarVisual(String c) {
        return (c != null && c.length() == 13) ? c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "." + c.substring(8) : c;
    }
}
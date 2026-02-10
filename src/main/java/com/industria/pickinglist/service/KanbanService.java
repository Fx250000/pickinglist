package com.industria.pickinglist.service;

import com.industria.pickinglist.dto.KanbanRelatorio;
import com.industria.pickinglist.repository.MovimentacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class KanbanService {

    @Autowired
    private MovimentacaoRepository repository;

    public List<KanbanRelatorio> gerarRelatorioKanban(int diasAnalise) {
        LocalDateTime dataCorte = LocalDateTime.now().minusDays(diasAnalise);

        // 1. Busca dados brutos
        List<Object[]> consumos = repository.somarConsumoPorPeriodo(dataCorte);
        List<Object[]> faltas = repository.somarFaltasPorPeriodo(dataCorte);

        // 2. Transforma Faltas em um Mapa para busca rápida
        // Map<Codigo, DadosFalta>
        Map<String, double[]> mapaFaltas = new HashMap<>();
        for (Object[] row : faltas) {
            String codigo = (String) row[0];
            Double qtdFalta = (Double) row[1];
            Long freqFalta = (Long) row[2];
            mapaFaltas.put(codigo, new double[]{qtdFalta, freqFalta.doubleValue()});
        }

        // 3. Monta a lista final calculada
        List<KanbanRelatorio> relatorio = new ArrayList<>();

        for (Object[] row : consumos) {
            String codigo = (String) row[0];
            String descricao = (String) row[1];
            Double totalConsumo = (Double) row[2];

            // Busca dados de falta (ou zero se não houve falta)
            double[] dadosFalta = mapaFaltas.getOrDefault(codigo, new double[]{0.0, 0.0});
            Double totalFalta = dadosFalta[0];
            Long freqFalta = (long) dadosFalta[1];

            // CÁLCULOS KANBAN
            Double consumoDiario = totalConsumo / diasAnalise;

            // Fator de Segurança: Sugere estoque para cobrir 5 dias de produção
            int diasSeguranca = 5;
            Double estoqueSugerido = consumoDiario * diasSeguranca;

            // Arredondamento para cima (Math.ceil) para evitar fração
            estoqueSugerido = Math.ceil(estoqueSugerido);

            relatorio.add(new KanbanRelatorio(
                    codigo, descricao, totalConsumo, totalFalta, freqFalta, consumoDiario, estoqueSugerido
            ));
        }

        // Ordenar por quem tem mais Faltas (Prioridade Crítica)
        relatorio.sort(Comparator.comparing(KanbanRelatorio::getFrequenciaFalta).reversed());

        return relatorio;
    }
}
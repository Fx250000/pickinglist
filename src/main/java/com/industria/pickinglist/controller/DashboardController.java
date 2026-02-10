package com.industria.pickinglist.controller; // Não esqueça o package

import com.industria.pickinglist.dto.KanbanRelatorio;
import com.industria.pickinglist.service.KanbanService;
import com.industria.pickinglist.service.PickingService; // Importar Service
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // Importar Model
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam; // Importar Param

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private KanbanService kanbanService;

    @Autowired
    private PickingService pickingService; // Necessário para buscar o histórico bruto

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String inicio,
                            @RequestParam(required = false) String fim,
                            Model model) {

        // Definição de datas (Igual ao histórico)
        LocalDateTime dataInicio = (inicio != null && !inicio.isEmpty())
                ? LocalDate.parse(inicio).atStartOfDay()
                : LocalDateTime.now().minusDays(30); // Padrão: Últimos 30 dias

        LocalDateTime dataFim = (fim != null && !fim.isEmpty())
                ? LocalDate.parse(fim).atTime(LocalTime.MAX)
                : LocalDateTime.now();

        // 1. Gera KPI Kanban
        // Calcula dias entre as datas para a média
        long dias = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim);
        if(dias < 1) dias = 1;

        List<KanbanRelatorio> kpis = kanbanService.gerarRelatorioKanban((int) dias);
        model.addAttribute("listaKanban", kpis);

        // 2. Totais para os Cards do topo
        double totalFaltasGeral = kpis.stream().mapToDouble(KanbanRelatorio::getFrequenciaFalta).sum();
        model.addAttribute("totalFaltas", totalFaltasGeral);

        // 3. Dados para as Abas de Detalhes (Correção para o HTML funcionar)
        model.addAttribute("consumos", pickingService.getAnalise(dataInicio, dataFim, "CONSUMO"));
        model.addAttribute("faltas", pickingService.getAnalise(dataInicio, dataFim, "FALTA"));

        // Repassa as datas para o filtro manter o valor
        model.addAttribute("dataInicio", dataInicio.toLocalDate());
        model.addAttribute("dataFim", dataFim.toLocalDate());

        return "dashboard";
    }
}
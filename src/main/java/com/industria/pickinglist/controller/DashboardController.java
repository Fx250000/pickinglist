package com.industria.pickinglist.controller;

import com.industria.pickinglist.dto.KanbanRelatorio;
import com.industria.pickinglist.service.KanbanService;
import com.industria.pickinglist.service.PickingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private KanbanService kanbanService;

    @Autowired
    private PickingService pickingService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // 1. Calculate stats (You can refine this logic in your Service later)
        List<KanbanRelatorio> kpis = kanbanService.gerarRelatorioKanban(30);

        // Mapping legacy data to new View Attributes
        model.addAttribute("completedOps", pickingService.getContagemOpsFinalizadas()); // Requires this method in Service
        model.addAttribute("totalOps", pickingService.getContagemOpsTotal());           // Requires this method in Service

        // Calculate Efficiency (Example Logic)
        double eficiencia = 95.5; // Placeholder or calculate based on (completed / total * 100)
        model.addAttribute("efficiency", eficiencia);

        // Alerts logic
        model.addAttribute("backlogAlerts", pickingService.getAlertasPendencia()); // Requires this method

        return "dashboard"; // Matches dashboard.html
    }
}
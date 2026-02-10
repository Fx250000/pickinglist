package com.industria.pickinglist.controller;

import com.industria.pickinglist.service.PickingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RelatorioController {

    @Autowired private PickingService service;

    @GetMapping("/relatorio-ops")
    public String relatorioOps(Model model) {
        model.addAttribute("ops", service.getHistoricoOps());
        return "relatorio-ops";
    }
}
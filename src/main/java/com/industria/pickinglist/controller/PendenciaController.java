package com.industria.pickinglist.controller;

import com.industria.pickinglist.model.Pendencia;
import com.industria.pickinglist.service.PickingService;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PendenciaController {

    @Autowired
    private PickingService service;

    @GetMapping("/pendencias")
    public String index(@RequestParam(required = false) String busca, Model model) {

        // Usa o novo método de serviço que já trata a busca
        List<Pendencia> listaAberta = service.buscarPendenciasAbertas(busca);

        // Agrupa por OP
        Map<String, List<Pendencia>> mapaPorOp = listaAberta.stream()
                .collect(Collectors.groupingBy(Pendencia::getOp));

        model.addAttribute("pendenciasMap", mapaPorOp);
        model.addAttribute("entregues", service.getHistoricoEntregas());
        model.addAttribute("busca", busca); // Para manter o texto no input

        return "pendencias";
    }

    @PostMapping("/pendencias/entregar")
    public String entregar(@RequestParam Long id) {
        service.realizarEntrega(id);
        return "redirect:/pendencias";
    }

    // --- NOVO: EXCLUIR OP COMPLETA ---
    @PostMapping("/pendencias/excluir-op")
    public String excluirOp(@RequestParam String op) {
        service.excluirPendenciasDaOp(op);
        return "redirect:/pendencias";
    }
}
package com.industria.pickinglist.controller;

import com.industria.pickinglist.model.Pendencia;
import com.industria.pickinglist.service.PickingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PendenciaController {

    @Autowired
    private PickingService service;

    @GetMapping("/pendencias")
    public String index(@RequestParam(required = false) String busca, Model model) {
        // 1. Busca os dados filtrados ou todos
        List<Pendencia> listaAberta = service.buscarPendenciasAbertas(busca);

        // 2. Agrupa por OP para o Accordion
        Map<String, List<Pendencia>> mapaPorOp = listaAberta.stream()
                .collect(Collectors.groupingBy(Pendencia::getOp));

        model.addAttribute("pendenciasMap", mapaPorOp);
        model.addAttribute("entregues", service.getHistoricoEntregas());
        model.addAttribute("busca", busca);
        return "pendencias";
    }

    // FIXED: Now accepts qtdEntregue parameter
    @PostMapping("/pendencias/entregar")
    public String entregar(@RequestParam Long id,
                           @RequestParam(required = false) Double qtdEntregue,
                           RedirectAttributes redirect) {
        service.realizarEntrega(id, qtdEntregue);
        redirect.addFlashAttribute("message", "Entrega registrada com sucesso!");
        return "redirect:/pendencias";
    }

    @PostMapping("/pendencias/excluir-op")
    public String excluirOp(@RequestParam String op, RedirectAttributes redirect) {
        service.excluirPendenciasDaOp(op);
        redirect.addFlashAttribute("message", "OP removida do backlog!");
        return "redirect:/pendencias";
    }
}

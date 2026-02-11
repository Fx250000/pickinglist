package com.industria.pickinglist.controller;

import com.industria.pickinglist.model.BacklogDTO; // Import the DTO
import com.industria.pickinglist.model.Pendencia;
import com.industria.pickinglist.service.PickingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    // --- VIEW: BACKLOG ---
    @GetMapping("/pendencias")
    public String index(@RequestParam(required = false) String busca, Model model) {
        List<Pendencia> listaAberta = service.buscarPendenciasAbertas(busca);

        Map<String, List<Pendencia>> mapaPorOp = listaAberta.stream()
                .collect(Collectors.groupingBy(Pendencia::getOp));

        model.addAttribute("pendenciasMap", mapaPorOp);
        model.addAttribute("entregues", service.getHistoricoEntregas());
        model.addAttribute("busca", busca);

        return "pendencias"; // Maps to pendencias.html
    }

    // --- ACTION: REGISTRAR ENTREGA ---
    @PostMapping("/pendencias/entregar")
    public String entregar(@RequestParam Long id,
                           @RequestParam(required = false) Double qtdEntregue,
                           RedirectAttributes redirect) {
        service.realizarEntrega(id, qtdEntregue);
        redirect.addFlashAttribute("message", "Entrega registrada com sucesso!");
        return "redirect:/pendencias";
    }

    // --- ACTION: EXCLUIR OP ---
    @PostMapping("/pendencias/excluir-op")
    public String excluirOp(@RequestParam String op, RedirectAttributes redirect) {
        service.excluirPendenciasDaOp(op);
        redirect.addFlashAttribute("message", "OP removida do backlog!");
        return "redirect:/pendencias";
    }

    // --- API: FINALIZAR OP (Called by JS) ---
    @PostMapping("/api/picking/finalize")
    public ResponseEntity<?> finalizePicking(@RequestBody List<BacklogDTO> items) {
        try {
            // Note: You must add 'salvarBacklog' to your PickingService
            service.salvarBacklog(items);
            return ResponseEntity.ok().body("{\"message\": \"Saved successfully\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"message\": \"Error saving backlog: " + e.getMessage() + "\"}");
        }
    }
}
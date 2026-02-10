package com.industria.pickinglist.controller;

import com.industria.pickinglist.service.PickingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
public class PickingController {

    @Autowired
    private PickingService service;

    // --- TELA PRINCIPAL ---
    @GetMapping("/")
    public String index(@RequestParam(required = false) List<String> zones, Model model) {
        model.addAttribute("itens", service.getListaFiltrada(zones));
        model.addAttribute("selectedZones", zones);
        model.addAttribute("op", service.getNumeroOP());
        return "checklist";
    }

    // --- FINALIZAR OP COM FILTRO ---
    @PostMapping("/finalizar-op")
    public String finalizarOp(@RequestParam(required = false) List<String> zones,
                              RedirectAttributes redirectAttributes) {

        // 1. Finaliza apenas as zonas marcadas (ou tudo se null)
        service.finalizarSeparacao(zones);

        // 2. Repassa o filtro para o relatório de impressão
        if (zones != null && !zones.isEmpty()) {
            redirectAttributes.addAttribute("zones", zones);
        }

        return "redirect:/producao";
    }

    // --- RELATÓRIO DE FALTAS (IMPRESSÃO) ---
    @GetMapping("/producao")
    public String relatorioProducao(@RequestParam(required = false) List<String> zones, Model model) {
        // Agora busca apenas faltantes das zonas filtradas
        model.addAttribute("faltantes", service.getFaltantes(zones));
        model.addAttribute("op", service.getNumeroOP());
        return "producao";
    }

    // --- UPLOAD ---
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        try {
            service.processarArquivo(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/";
    }
}
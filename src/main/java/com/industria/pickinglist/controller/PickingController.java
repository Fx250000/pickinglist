package com.industria.pickinglist.controller;

import com.industria.pickinglist.service.PickingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class PickingController {

    @Autowired
    private PickingService service;

    // --- TELA PRINCIPAL (PICKING) ---
    @GetMapping("/")
    public String index(@RequestParam(required = false) List<String> zones, Model model) {
        // 'pickingList' matches the variable used in index.html
        model.addAttribute("pickingList", service.getListaFiltrada(zones));
        model.addAttribute("opId", service.getNumeroOP());
        return "index"; // Matches the new index.html file
    }

    // --- ACTION: UPLOAD CSV ---
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
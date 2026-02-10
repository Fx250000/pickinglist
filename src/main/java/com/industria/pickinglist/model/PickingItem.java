package com.industria.pickinglist.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class PickingItem {
    private String id;
    private String codigo;
    private String descricao;
    private String local;
    private String unidade;
    private Double qtdRequerida; // O que o Excel pediu
    private Double qtdSeparada = 0.0; // O que você digitou ou marcou

    // Controle
    private boolean isPai;
    private String status; // "PENDENTE", "PARCIAL", "OK"
    private String zona;

    // Campos Kit
    private String parentId;
    private String codigoPai;
    private List<String> childrenIds = new ArrayList<>();

    public PickingItem() {
        this.id = UUID.randomUUID().toString();
        this.status = "PENDENTE";
    }

    public void atualizarStatus() {
        if (qtdSeparada == null) qtdSeparada = 0.0;

        // Tolerância para ponto flutuante (0.001)
        if (qtdSeparada >= (qtdRequerida - 0.001)) {
            this.status = "OK";
            this.qtdSeparada = this.qtdRequerida; // Trava no máximo
        } else if (qtdSeparada > 0.001) {
            this.status = "PARCIAL";
        } else {
            this.status = "PENDENTE";
            this.qtdSeparada = 0.0;
        }
    }

    // --- CORREÇÃO CRÍTICA AQUI ---
    // Este método calcula o que vai para o relatório de faltas
    public Double getSaldoDevedor() {
        if (qtdSeparada == null) qtdSeparada = 0.0;
        if (qtdRequerida == null) qtdRequerida = 0.0;

        double saldo = qtdRequerida - qtdSeparada;

        // Se o saldo for negativo (separou a mais) ou muito perto de zero, retorna 0
        if (saldo < 0.001) {
            return 0.0;
        }

        return saldo;
    }
}
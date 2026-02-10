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
    private Double qtdRequerida; // O que pediu
    private Double qtdSeparada = 0.0; // O que encontrou

    private boolean isPai;
    private String status; // "PENDENTE", "PARCIAL", "OK"
    private String zona;

    private String parentId;
    private String codigoPai;
    private List<String> childrenIds = new ArrayList<>();

    public PickingItem() {
        this.id = UUID.randomUUID().toString();
        this.status = "PENDENTE";
    }

    public void atualizarStatus() {
        if (qtdSeparada == null) qtdSeparada = 0.0;
        if (qtdRequerida == null) qtdRequerida = 0.0;

        // Tolerância para ponto flutuante
        if (qtdSeparada >= (qtdRequerida - 0.001)) {
            this.status = "OK";
            this.qtdSeparada = this.qtdRequerida; // Trava no teto
        } else if (qtdSeparada > 0.001) {
            this.status = "PARCIAL";
        } else {
            this.status = "PENDENTE";
            this.qtdSeparada = 0.0;
        }
    }

    public Double getSaldoDevedor() {
        if (qtdSeparada == null) qtdSeparada = 0.0;
        double saldo = qtdRequerida - qtdSeparada;
        return (saldo < 0.001) ? 0.0 : saldo;
    }
}
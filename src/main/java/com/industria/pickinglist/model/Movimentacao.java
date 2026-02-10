package com.industria.pickinglist.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String op;
    private String codigo;
    private String descricao;
    private String unidade;
    private Double quantidade;

    // NOVO CAMPO: "CONSUMO" (Check verde) ou "FALTA" (Não tinha na prateleira)
    private String tipo;

    private LocalDateTime dataMovimentacao;

    public Movimentacao() {}

    public Movimentacao(String op, String codigo, String descricao, String unidade, Double quantidade, String tipo) {
        this.op = op;
        this.codigo = codigo;
        this.descricao = descricao;
        this.unidade = unidade;
        this.quantidade = quantidade;
        this.tipo = tipo; // <--- Recebe o tipo
        this.dataMovimentacao = LocalDateTime.now();
    }
}
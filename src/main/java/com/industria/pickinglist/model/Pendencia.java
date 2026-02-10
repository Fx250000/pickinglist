package com.industria.pickinglist.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Data
public class Pendencia {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String op;
    private String codigo;
    private String descricao;
    private String unidade;

    // O QUE FALTOU (Saldo Devedor) - É o que precisa ser entregue
    private Double quantidade;

    // NOVO CAMPO: O que foi pedido originalmente na lista
    private Double qtdOriginal;

    private LocalDateTime dataAbertura;
    private LocalDateTime dataEntrega;
    private String status; // "ABERTO", "ENTREGUE"
    private String tempoEspera;

    public void registrarEntrega() {
        this.dataEntrega = LocalDateTime.now();
        this.status = "ENTREGUE";

        Duration duracao = Duration.between(dataAbertura, dataEntrega);
        long dias = duracao.toDays();
        long horas = duracao.toHours() % 24;
        this.tempoEspera = String.format("%d dias e %d horas", dias, horas);
    }

    // Método auxiliar para mostrar quanto foi encontrado antes de gerar a falta
    public Double getQtdEncontrada() {
        if (qtdOriginal == null) return 0.0;
        if (quantidade == null) return 0.0;
        double encontrada = qtdOriginal - quantidade;
        return (encontrada < 0) ? 0.0 : encontrada;
    }
}
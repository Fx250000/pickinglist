package com.industria.pickinglist.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Data
public class HistoricoOp {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String op;
    private LocalDateTime dataFinalizacao;

    private Double totalItens;
    private Double itensAtendidos;
    private Double percentualAtendimento; // Ex: 95.5%

    // Método auxiliar para o Thymeleaf calcular o tempo parado
    public String getTempoParado() {
        Duration d = Duration.between(dataFinalizacao, LocalDateTime.now());
        long dias = d.toDays();
        long horas = d.toHours() % 24;

        if (dias == 0 && horas == 0) return "Menos de 1h";
        return String.format("%d dias e %d horas", dias, horas);
    }
}
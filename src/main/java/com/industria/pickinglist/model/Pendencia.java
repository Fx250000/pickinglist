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

    private String op;          // Para saber para onde encaminhar
    private String codigo;
    private String descricao;
    private Double quantidade;
    private String unidade;

    private LocalDateTime dataAbertura; // Quando foi detectada a falta
    private LocalDateTime dataEntrega;  // Quando foi resolvido

    private String status; // "ABERTO" ou "ENTREGUE"

    // Campo calculado: Tempo em horas/minutos que levou
    private String tempoEspera;

    public void registrarEntrega() {
        this.dataEntrega = LocalDateTime.now();
        this.status = "ENTREGUE";

        // Lógica do tempo decorrido
        Duration duracao = Duration.between(dataAbertura, dataEntrega);
        long dias = duracao.toDays();
        long horas = duracao.toHours() % 24;
        this.tempoEspera = String.format("%d dias e %d horas", dias, horas);
    }
}
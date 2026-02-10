package com.industria.pickinglist.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Data
public class Pendencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String op;
    private String codigo;
    private String descricao;
    private String unidade;

    // DEFINIÇÃO CLARA DOS CAMPOS NUMÉRICOS
    private Double qtdOriginal;      // O quanto a OP pedia (Meta)
    private Double quantidade;       // O quanto FICOU FALTANDO (Saldo Devedor) - CAMPO LEGADO
    private Double qtdSeparada;      // Encontrado na separação inicial
    private Double qtdEntregada = 0.0;  // Entregue depois (backlog)
    private Double qtdRestante;      // qtdOriginal - qtdSeparada - qtdEntregada

    private LocalDateTime dataAbertura;
    private LocalDateTime dataEntrega;       // Para histórico de entregas antigas
    private LocalDateTime dataUltimaEntrega; // Última entrega parcial
    private String status;           // ABERTO, PARCIAL, CONCLUIDO
    private String tempoEspera;      // String formatada (ex: "2 dias, 3h")

    // =================================================================================
    // CLCULO AUXILIAR PARA O HTML
    // =================================================================================

    /**
     * Retorna quanto já foi entregue/separado na primeira leva
     * (compatibilidade com código antigo)
     */
    public Double getQtdEncontrada() {
        if (qtdOriginal == null) qtdOriginal = 0.0;
        if (quantidade == null) quantidade = 0.0;
        double encontrada = qtdOriginal - quantidade;
        // Evita números negativos por erro de arredondamento
        return encontrada < 0 ? 0.0 : encontrada;
    }

    /**
     * Total encontrado (separação inicial + entregas posteriores)
     */
    public Double getQtdEncontradaTotal() {
        if (qtdSeparada == null) qtdSeparada = 0.0;
        if (qtdEntregada == null) qtdEntregada = 0.0;
        return qtdSeparada + qtdEntregada;
    }

    /**
     * Quantidade que ainda falta (com recalculo)
     */
    public Double getQtdFaltante() {
        recalcularRestante();
        return qtdRestante;
    }

    /**
     * Verifica se pendência ainda está aberta
     */
    public boolean isAberta() {
        return "ABERTO".equals(status) || "PARCIAL".equals(status);
    }

    // =================================================================================
    // MÉTODOS DE REGISTRO DE ENTREGA
    // =================================================================================

    /**
     * Registra entrega completa (versão antiga - sem parâmetro)
     */
    public void registrarEntrega() {
        this.dataEntrega = LocalDateTime.now();
        this.dataUltimaEntrega = LocalDateTime.now();
        this.status = "ENTREGUE";

        // Calcula tempoEspera
        if (dataAbertura != null) {
            Duration duracao = Duration.between(dataAbertura, dataEntrega);
            long dias = duracao.toDays();
            long horas = duracao.toHours() % 24;
            if (dias == 0 && horas == 0) {
                this.tempoEspera = "Menos de 1h";
            } else {
                this.tempoEspera = String.format("%d dias, %d h", dias, horas);
            }
        }
    }

    /**
     * Registra entrega parcial com quantidade específica
     */
    public void registrarEntrega(Double qtdNovaEntrega) {
        if (qtdNovaEntrega == null || qtdNovaEntrega <= 0) {
            qtdNovaEntrega = this.qtdRestante != null ? this.qtdRestante : 0.0;
        }

        if (this.qtdEntregada == null) this.qtdEntregada = 0.0;
        this.qtdEntregada += qtdNovaEntrega;

        recalcularRestante();
        this.dataUltimaEntrega = LocalDateTime.now();

        if (qtdRestante < 0.001) {
            this.status = "CONCLUIDO";
            this.dataEntrega = LocalDateTime.now(); // Marca como entregue
        } else {
            this.status = "PARCIAL";
        }

        // Atualiza tempo de espera
        if (dataAbertura != null && dataUltimaEntrega != null) {
            Duration duracao = Duration.between(dataAbertura, dataUltimaEntrega);
            long dias = duracao.toDays();
            long horas = duracao.toHours() % 24;
            if (dias == 0 && horas == 0) {
                this.tempoEspera = "Menos de 1h";
            } else {
                this.tempoEspera = String.format("%d dias, %d h", dias, horas);
            }
        }
    }

    /**
     * Recalcula quantidade restante
     */
    private void recalcularRestante() {
        if (qtdOriginal == null) qtdOriginal = 0.0;
        if (qtdSeparada == null) qtdSeparada = 0.0;
        if (qtdEntregada == null) qtdEntregada = 0.0;

        this.qtdRestante = Math.max(0, qtdOriginal - qtdSeparada - qtdEntregada);
        this.quantidade = this.qtdRestante; // Mantém sincronizado com campo legado
    }

    /**
     * Setter para quantidade (compatibilidade com código legado)
     * Também inicializa qtdRestante
     */
    public void setQuantidade(Double quantidade) {
        this.quantidade = quantidade;
        this.qtdRestante = quantidade;
    }
}

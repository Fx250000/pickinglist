package com.industria.pickinglist.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KanbanRelatorio {
    private String codigo;
    private String descricao;
    private Double totalConsumido; // Soma de "CONSUMO"
    private Double totalFalta;     // Soma de "FALTA" (Quantidade que deixou de ser produzida)
    private Long frequenciaFalta;  // Quantas vezes (linhas) apareceu como falta
    private Double consumoDiario;  // Média
    private Double estoqueSugerido; // O "Kanban" (Ex: Média * 3 dias de margem)
}
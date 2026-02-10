package com.industria.pickinglist.repository;

import com.industria.pickinglist.model.Movimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    // Buscar por OP
    List<Movimentacao> findByOp(String op);

    // Buscar por período (Data Inicial e Final)
    @Query("SELECT m FROM Movimentacao m WHERE m.dataMovimentacao BETWEEN :inicio AND :fim")
    List<Movimentacao> buscarPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 1. Soma total de CONSUMO por item nos últimos X dias
    @Query("SELECT m.codigo, m.descricao, SUM(m.quantidade) " +
            "FROM Movimentacao m " +
            "WHERE m.tipo = 'CONSUMO' AND m.dataMovimentacao >= :dataInicio " +
            "GROUP BY m.codigo, m.descricao")
    List<Object[]> somarConsumoPorPeriodo(@Param("dataInicio") LocalDateTime dataInicio);

    // 2. Soma total e contagem de FALTA por item nos últimos X dias
    @Query("SELECT m.codigo, SUM(m.quantidade), COUNT(m) " +
            "FROM Movimentacao m " +
            "WHERE m.tipo = 'FALTA' AND m.dataMovimentacao >= :dataInicio " +
            "GROUP BY m.codigo")
    List<Object[]> somarFaltasPorPeriodo(@Param("dataInicio") LocalDateTime dataInicio);
}
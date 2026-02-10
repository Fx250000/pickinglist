package com.industria.pickinglist.repository;

import com.industria.pickinglist.model.Pendencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PendenciaRepository extends JpaRepository<Pendencia, Long> {

    List<Pendencia> findByStatus(String status);

    List<Pendencia> findByStatusOrderByDataEntregaDesc(String status);

    // --- NOVOS MÉTODOS ---

    // Deletar todas as pendências de uma OP
    void deleteByOp(String op);

    // Buscar peça contendo o trecho (funciona para os últimos 5 dígitos)
    List<Pendencia> findByCodigoContainingAndStatus(String codigo, String status);

    @Query("SELECT p FROM Pendencia p WHERE " +
            "(p.codigo LIKE %:termo% OR p.descricao LIKE %:termo%) " +
            "AND (p.status = :status1 OR p.status = :status2)")
    List<Pendencia> findByCodigoContainingAndStatusOrStatus(
            @Param("termo") String termo,
            @Param("status1") String status1,
            @Param("status2") String status2);

    @Query("SELECT p FROM Pendencia p WHERE p.status IN :statuses ORDER BY p.dataUltimaEntrega DESC")
    List<Pendencia> findByStatusInOrderByDataUltimaEntregaDesc(@Param("statuses") List<String> statuses);

}
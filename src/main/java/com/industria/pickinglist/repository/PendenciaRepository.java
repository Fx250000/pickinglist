package com.industria.pickinglist.repository;

import com.industria.pickinglist.model.Pendencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PendenciaRepository extends JpaRepository<Pendencia, Long> {

    List<Pendencia> findByStatus(String status);

    List<Pendencia> findByStatusOrderByDataEntregaDesc(String status);

    // --- NOVOS MÉTODOS ---

    // Deletar todas as pendências de uma OP
    void deleteByOp(String op);

    // Buscar peça contendo o trecho (funciona para os últimos 5 dígitos)
    List<Pendencia> findByCodigoContainingAndStatus(String codigo, String status);
}
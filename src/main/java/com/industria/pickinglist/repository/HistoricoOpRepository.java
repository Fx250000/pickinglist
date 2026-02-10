package com.industria.pickinglist.repository;

import com.industria.pickinglist.model.HistoricoOp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricoOpRepository extends JpaRepository<HistoricoOp, Long> {
    // Ordena do mais recente para o mais antigo
    List<HistoricoOp> findAllByOrderByDataFinalizacaoDesc();


}

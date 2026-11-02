package com.planejador.repository;

import com.planejador.model.Orcamento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrcamentoRepository extends JpaRepository<Orcamento, UUID> {

    List<Orcamento> findByUsuarioIdOrderByAnoDescMesDesc(UUID usuarioId);
    Optional<Orcamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);
    Optional<Orcamento> findByUsuarioIdAndAnoAndMes(UUID usuarioId, int ano, int mes);
}

package com.planejador.repository;

import com.planejador.model.Fatura;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    Optional<Fatura> findByIdAndCartaoUsuarioId(UUID id, UUID usuarioId);
    Optional<Fatura> findByCartaoIdAndMesReferenciaAndAnoReferencia(UUID cartaoId, int mes, int ano);
    boolean existsByCartaoId(UUID cartaoId);
}

package com.planejador.repository;

import com.planejador.model.Transferencia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransferenciaRepository extends JpaRepository<Transferencia, UUID>, JpaSpecificationExecutor<Transferencia> {

    Optional<Transferencia> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}

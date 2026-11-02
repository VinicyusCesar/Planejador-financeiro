package com.planejador.repository;

import com.planejador.model.Conta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, UUID> {

    List<Conta> findByUsuarioIdOrderByNomeAsc(UUID usuarioId);
    Optional<Conta> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}

package com.planejador.repository;

import com.planejador.model.CartaoCredito;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartaoCreditoRepository extends JpaRepository<CartaoCredito, UUID> {

    List<CartaoCredito> findByUsuarioIdOrderByNomeAsc(UUID usuarioId);
    Optional<CartaoCredito> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}

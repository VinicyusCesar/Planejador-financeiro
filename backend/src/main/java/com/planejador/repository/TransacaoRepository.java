package com.planejador.repository;

import com.planejador.model.Transacao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransacaoRepository extends JpaRepository<Transacao, UUID>, JpaSpecificationExecutor<Transacao> {

    Optional<Transacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);
    boolean existsByCategoriaId(UUID categoriaId);
}

package com.planejador.repository;

import com.planejador.enums.TipoCategoria;
import com.planejador.model.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    List<Categoria> findByUsuarioIdOrderByNomeAsc(UUID usuarioId);
    Optional<Categoria> findByIdAndUsuarioId(UUID id, UUID usuarioId);
    Optional<Categoria> findByUsuarioIdAndNomeAndTipo(UUID usuarioId, String nome, TipoCategoria tipo);
}

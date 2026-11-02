package com.planejador.repository;

import com.planejador.model.ItemOrcamento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemOrcamentoRepository extends JpaRepository<ItemOrcamento, UUID> {

    @EntityGraph(attributePaths = {"categoria"})
    List<ItemOrcamento> findByOrcamentoId(UUID orcamentoId);
    boolean existsByCategoriaId(UUID categoriaId);
}

package com.planejador.repository;

import com.planejador.model.MetaFinanceira;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaFinanceiraRepository extends JpaRepository<MetaFinanceira, UUID> {

    List<MetaFinanceira> findByUsuarioIdOrderByDataInicioDesc(UUID usuarioId);
    Optional<MetaFinanceira> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}

package com.planejador.repository;

import com.planejador.model.PagamentoFatura;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoFaturaRepository extends JpaRepository<PagamentoFatura, UUID> {

    boolean existsByFaturaId(UUID faturaId);
    List<PagamentoFatura> findByFaturaIdOrderByDataPagamentoAsc(UUID faturaId);
}

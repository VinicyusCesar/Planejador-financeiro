package com.planejador.repository;

import com.planejador.enums.SituacaoTransacao;
import com.planejador.model.Parcela;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelaRepository extends JpaRepository<Parcela, UUID> {

    @EntityGraph(attributePaths = {"fatura"})
    List<Parcela> findByTransacaoIdOrderByNumeroAsc(UUID transacaoId);
    @EntityGraph(attributePaths = {"transacao"})
    List<Parcela> findByFaturaIdAndTransacaoSituacaoOrderByNumeroAsc(UUID faturaId, SituacaoTransacao situacao);
}

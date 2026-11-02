package com.planejador.repository;

import com.planejador.dto.DashboardDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultasFinanceirasRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasFinanceirasRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Totais(BigDecimal receitas, BigDecimal despesas) {}
    public record ValoresFatura(UUID id, UUID cartaoId, int mes, int ano, LocalDate fechamento,
            LocalDate vencimento, BigDecimal total, BigDecimal pago, BigDecimal pendente) {}

    public BigDecimal saldo(UUID usuarioId, UUID contaId, LocalDate hoje) {
        return jdbc.queryForObject("""
                SELECT c.saldo_inicial
                  + COALESCE((SELECT SUM(CASE WHEN t.tipo = 'RECEITA' THEN t.valor ELSE -t.valor END)
                      FROM transacao t WHERE t.conta_id = c.id AND t.usuario_id = c.usuario_id
                        AND t.situacao = 'CONFIRMADA' AND t.meio_pagamento <> 'CREDITO'
                        AND t.data_liquidacao IS NOT NULL AND t.data_liquidacao <= :hoje), 0)
                  + COALESCE((SELECT SUM(tr.valor) FROM transferencia tr
                      WHERE tr.conta_destino_id = c.id AND tr.usuario_id = c.usuario_id
                        AND tr.situacao = 'CONFIRMADA' AND tr.data_transferencia <= :hoje), 0)
                  - COALESCE((SELECT SUM(tr.valor) FROM transferencia tr
                      WHERE tr.conta_origem_id = c.id AND tr.usuario_id = c.usuario_id
                        AND tr.situacao = 'CONFIRMADA' AND tr.data_transferencia <= :hoje), 0)
                  - COALESCE((SELECT SUM(p.valor) FROM pagamento_fatura p
                      JOIN fatura f ON f.id = p.fatura_id JOIN cartao_credito cc ON cc.id = f.cartao_id
                      WHERE p.conta_id = c.id AND cc.usuario_id = c.usuario_id AND p.data_pagamento <= :hoje), 0)
                  AS saldo
                FROM conta c WHERE c.id = :contaId AND c.usuario_id = :usuarioId
                """, Map.of("usuarioId", usuarioId, "contaId", contaId, "hoje", hoje), BigDecimal.class);
    }

    public Totais totais(UUID usuarioId, LocalDate inicio, LocalDate fimExclusivo) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'RECEITA' THEN valor ELSE 0 END), 0) AS receitas,
                       COALESCE(SUM(CASE WHEN tipo = 'DESPESA' THEN valor ELSE 0 END), 0) AS despesas
                FROM transacao WHERE usuario_id = :usuarioId AND situacao = 'CONFIRMADA'
                  AND data_transacao >= :inicio AND data_transacao < :fim
                """, periodo(usuarioId, inicio, fimExclusivo), (rs, row) ->
                new Totais(rs.getBigDecimal("receitas"), rs.getBigDecimal("despesas")));
    }

    public List<DashboardDto.CategoriaGasto> categorias(UUID usuarioId, LocalDate inicio, LocalDate fimExclusivo) {
        return jdbc.query("""
                SELECT c.id, c.nome, SUM(t.valor) AS total FROM transacao t
                JOIN categoria c ON c.id = t.categoria_id AND c.usuario_id = t.usuario_id
                WHERE t.usuario_id = :usuarioId AND t.situacao = 'CONFIRMADA' AND t.tipo = 'DESPESA'
                  AND t.data_transacao >= :inicio AND t.data_transacao < :fim
                GROUP BY c.id, c.nome ORDER BY total DESC, c.nome, c.id
                """, periodo(usuarioId, inicio, fimExclusivo), (rs, row) -> new DashboardDto.CategoriaGasto(
                rs.getObject("id", UUID.class), rs.getString("nome"), rs.getBigDecimal("total")));
    }

    public List<DashboardDto.DiaGasto> dias(UUID usuarioId, LocalDate inicio, LocalDate fimExclusivo) {
        return jdbc.query("""
                SELECT data_transacao, SUM(valor) AS total FROM transacao
                WHERE usuario_id = :usuarioId AND situacao = 'CONFIRMADA' AND tipo = 'DESPESA'
                  AND data_transacao >= :inicio AND data_transacao < :fim
                GROUP BY data_transacao ORDER BY data_transacao
                """, periodo(usuarioId, inicio, fimExclusivo), (rs, row) -> new DashboardDto.DiaGasto(
                rs.getObject("data_transacao", LocalDate.class), rs.getBigDecimal("total")));
    }

    public BigDecimal previsto(UUID usuarioId, int ano, int mes) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.valor_previsto), 0) FROM item_orcamento i
                JOIN orcamento o ON o.id = i.orcamento_id
                WHERE o.usuario_id = :usuarioId AND o.ano = :ano AND o.mes = :mes
                """, Map.of("usuarioId", usuarioId, "ano", ano, "mes", mes), BigDecimal.class);
    }

    public List<ValoresFatura> faturas(UUID usuarioId, UUID cartaoId, LocalDate hoje) {
        return valoresFaturas(usuarioId, cartaoId, null, hoje);
    }

    public ValoresFatura fatura(UUID usuarioId, UUID faturaId, LocalDate hoje) {
        return valoresFaturas(usuarioId, null, faturaId, hoje).getFirst();
    }

    public BigDecimal limiteUtilizado(UUID usuarioId, UUID cartaoId, LocalDate hoje) {
        return faturas(usuarioId, cartaoId, hoje).stream().map(ValoresFatura::pendente)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    private List<ValoresFatura> valoresFaturas(UUID usuarioId, UUID cartaoId, UUID faturaId, LocalDate hoje) {
        String filtro = (cartaoId == null ? "" : " AND f.cartao_id = :cartaoId")
                + (faturaId == null ? "" : " AND f.id = :faturaId");
        MapSqlParameterSource parametros = new MapSqlParameterSource("usuarioId", usuarioId)
                .addValue("hoje", hoje).addValue("cartaoId", cartaoId).addValue("faturaId", faturaId);
        // Parcelas e pagamentos são somados separadamente para não multiplicar valores no JOIN.
        String sql = """
                WITH faturas_usuario AS (
                    SELECT f.* FROM fatura f JOIN cartao_credito c ON c.id = f.cartao_id
                    WHERE c.usuario_id = :usuarioId
                """ + filtro + """
                ), compras AS (
                    SELECT p.fatura_id, SUM(p.valor) AS total FROM parcela p
                    JOIN transacao t ON t.id = p.transacao_id
                    JOIN faturas_usuario f ON f.id = p.fatura_id
                    WHERE t.situacao = 'CONFIRMADA' AND t.usuario_id = :usuarioId GROUP BY p.fatura_id
                ), pagamentos AS (
                    SELECT p.fatura_id, SUM(p.valor) AS total FROM pagamento_fatura p
                    JOIN faturas_usuario f ON f.id = p.fatura_id
                    WHERE p.data_pagamento <= :hoje GROUP BY p.fatura_id
                )
                SELECT f.id, f.cartao_id, f.mes_referencia, f.ano_referencia,
                       f.data_fechamento, f.data_vencimento, COALESCE(c.total, 0) AS total,
                       COALESCE(p.total, 0) AS pago,
                       GREATEST(COALESCE(c.total, 0) - COALESCE(p.total, 0), 0) AS pendente
                FROM faturas_usuario f LEFT JOIN compras c ON c.fatura_id = f.id
                LEFT JOIN pagamentos p ON p.fatura_id = f.id
                ORDER BY f.ano_referencia, f.mes_referencia, f.cartao_id
                """;
        return jdbc.query(sql, parametros, (rs, row) -> new ValoresFatura(
                rs.getObject("id", UUID.class), rs.getObject("cartao_id", UUID.class),
                rs.getInt("mes_referencia"), rs.getInt("ano_referencia"),
                rs.getObject("data_fechamento", LocalDate.class), rs.getObject("data_vencimento", LocalDate.class),
                rs.getBigDecimal("total"), rs.getBigDecimal("pago"), rs.getBigDecimal("pendente")));
    }

    private Map<String, Object> periodo(UUID usuarioId, LocalDate inicio, LocalDate fim) {
        return Map.of("usuarioId", usuarioId, "inicio", inicio, "fim", fim);
    }
}

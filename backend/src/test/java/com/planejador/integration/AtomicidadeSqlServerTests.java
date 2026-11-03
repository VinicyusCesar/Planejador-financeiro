package com.planejador.integration;

import com.planejador.dto.CategoriaDto;
import com.planejador.enums.TipoCategoria;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SQLSERVER_TEST_ENABLED", matches = "true")
class AtomicidadeSqlServerTests extends SuporteSqlServer {
    @Test
    void falhaDepoisDeGravarCompraFaturasEParcelasReverteTodasAsTabelas() {
        var tx = new TransactionTemplate(gerenciadorTransacoes);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            transacoes.criar(usuarioId, credito("100.00", 3, LocalDate.of(2026, 10, 20)));
            entityManager.flush();
            throw new IllegalStateException("Falha injetada depois do flush");
        })).isInstanceOf(IllegalStateException.class);
        for (String tabela : new String[]{"transacao", "fatura", "parcela"}) {
            String filtro = switch (tabela) {
                case "transacao" -> "usuario_id = :usuario";
                case "fatura" -> "cartao_id IN (SELECT id FROM cartao_credito WHERE usuario_id = :usuario)";
                default -> "transacao_id IN (SELECT id FROM transacao WHERE usuario_id = :usuario)";
            };
            Number quantidade = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM dbo." + tabela + " WHERE " + filtro)
                    .setParameter("usuario", usuarioId).getSingleResult();
            assertThat(quantidade.intValue()).as("rollback de %s", tabela).isZero();
        }
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("0.00");
    }

    @Test
    void constraintsRejeitamViolacoesPorSqlDireto() {
        rejeitar("INSERT INTO dbo.conta(usuario_id,nome,tipo,saldo_inicial) VALUES (:usuario,'Invalida','TIPO_INVALIDO',0)");
        rejeitar("INSERT INTO dbo.meta_financeira(usuario_id,nome,valor_objetivo,data_inicio) VALUES (:usuario,'Invalida',-1,'20261026')");
        rejeitar("INSERT INTO dbo.conta(usuario_id,nome,tipo) VALUES (NEWID(),'Sem proprietario','CORRENTE')", false);
        rejeitar("INSERT INTO dbo.conta(usuario_id,nome,tipo) VALUES (:usuario,NULL,'CORRENTE')");
        rejeitar("INSERT INTO dbo.categoria(usuario_id,nome,tipo) SELECT usuario_id,nome,tipo FROM dbo.categoria WHERE usuario_id=:usuario");
    }

    private void rejeitar(String sql) { rejeitar(sql, true); }

    private void rejeitar(String sql, boolean parametro) {
        assertThatThrownBy(() -> new TransactionTemplate(gerenciadorTransacoes).executeWithoutResult(status -> {
            var query = entityManager.createNativeQuery(sql);
            if (parametro) query.setParameter("usuario", usuarioId);
            query.executeUpdate();
        })).isInstanceOf(RuntimeException.class).satisfies(erro -> {
            Throwable causa = erro;
            while (causa != null && !(causa instanceof java.sql.SQLException)) causa = causa.getCause();
            assertThat(causa).as("erro real de constraint SQL Server").isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException) causa).getErrorCode()).isIn(547, 515, 2601, 2627);
        });
        // Uma violacao anterior nao deve inutilizar a proxima transacao.
        assertThat(contas.consultar(usuarioId, contaId).id()).isEqualTo(contaId);
    }

    @Test
    void crudPersisteAtualizacaoEExclusaoPermitida() {
        var nova = categorias.criar(usuarioId, new CategoriaDto.Entrada("CRUD", TipoCategoria.AMBOS));
        categorias.atualizar(usuarioId, nova.id(), new CategoriaDto.Entrada("CRUD atualizado", TipoCategoria.DESPESA));
        String nome = (String) entityManager.createNativeQuery("SELECT nome FROM dbo.categoria WHERE id=:id")
                .setParameter("id", nova.id()).getSingleResult();
        assertThat(nome).isEqualTo("CRUD atualizado");
        categorias.excluir(usuarioId, nova.id());
        Number restante = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM dbo.categoria WHERE id=:id")
                .setParameter("id", nova.id()).getSingleResult();
        assertThat(restante.intValue()).isZero();
    }
}

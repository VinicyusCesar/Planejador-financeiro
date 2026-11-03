package com.planejador.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SqlServerAuthenticationConfigTests {
    private HikariDataSource datasource() {
        var ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:sqlserver://localhost:15433;databaseName=PlanejadorFinanceiroTest;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true");
        return ds;
    }

    @Test
    void aceitaSomenteConfiguracaoNativaSemCredenciais() {
        try (var ds = datasource()) {
            assertThat(SqlServerAuthenticationConfig.exigirIdentidadeWindows()
                    .postProcessBeforeInitialization(ds, "dataSource")).isSameAs(ds);
        }
    }

    @Test
    void rejeitaCredencialInjetadaPorConfiguracaoExterna() {
        try (var ds = datasource()) {
            ds.setUsername("usuario_sql_proibido");
            assertThatThrownBy(() -> SqlServerAuthenticationConfig.exigirIdentidadeWindows()
                    .postProcessBeforeInitialization(ds, "dataSource")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejeitaUrlQueDesabilitaAutenticacaoIntegrada() {
        try (var ds = datasource()) {
            ds.setJdbcUrl(ds.getJdbcUrl().replace("integratedSecurity=true", "integratedSecurity=false"));
            assertThatThrownBy(() -> SqlServerAuthenticationConfig.exigirIdentidadeWindows()
                    .postProcessBeforeInitialization(ds, "dataSource")).isInstanceOf(IllegalStateException.class);
        }
    }
}

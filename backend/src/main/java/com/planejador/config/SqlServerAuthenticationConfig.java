package com.planejador.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Locale;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("sqlserver")
public class SqlServerAuthenticationConfig {
    @Bean
    static BeanPostProcessor exigirIdentidadeWindows() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof HikariDataSource ds) {
                    String url = ds.getJdbcUrl() == null ? "" : ds.getJdbcUrl();
                    String normalizada = url.toLowerCase(Locale.ROOT);
                    if (!normalizada.startsWith("jdbc:sqlserver://")
                            || !normalizada.matches("(?s).*;integratedsecurity=true(?:;.*)?")
                            || !normalizada.matches("(?s).*;authenticationscheme=nativeauthentication(?:;.*)?")
                            || normalizada.matches("(?s).*;(user|username|password|authentication|accesstoken)=.*")
                            || ds.getUsername() != null || ds.getPassword() != null
                            || !ds.getDataSourceProperties().isEmpty()) {
                        throw new IllegalStateException("Profile sqlserver exige Windows NativeAuthentication sem credenciais ou propriedades alternativas de autenticacao.");
                    }
                }
                return bean;
            }
        };
    }
}

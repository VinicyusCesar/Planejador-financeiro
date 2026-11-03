package com.planejador.repository;

import com.planejador.dto.DatabaseHealthResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;

@Repository
public class DatabaseHealthRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean sqlserver;

    public DatabaseHealthRepository(JdbcTemplate jdbcTemplate,
            @Value("${financeiro.sqlserver:false}") boolean sqlserver) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlserver = sqlserver;
    }

    public DatabaseHealthResponse checkConnection() {
        return jdbcTemplate.queryForObject(sqlserver ? """
                SELECT DB_NAME() AS database_name, SCHEMA_NAME() AS schema_name,
                       SUSER_SNAME() AS user_name
                """ : """
                SELECT current_database() AS database_name,
                       current_schema() AS schema_name,
                       current_user AS user_name
                """, (resultSet, rowNumber) -> new DatabaseHealthResponse(
                true,
                resultSet.getString("database_name"),
                resultSet.getString("schema_name"),
                resultSet.getString("user_name")
        ));
    }
}

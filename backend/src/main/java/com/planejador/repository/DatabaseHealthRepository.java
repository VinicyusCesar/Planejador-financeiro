package com.planejador.repository;

import com.planejador.dto.DatabaseHealthResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseHealthResponse checkConnection() {
        return jdbcTemplate.queryForObject("""
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

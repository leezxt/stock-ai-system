package com.example.stockai.database;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
class PostgresDatabaseConfig {
    @Bean
    @Conditional(DatabaseUrlCondition.class)
    DataSource stockAiDataSource(Environment environment) {
        String rawUrl = DatabaseSettings.resolveUrl(environment);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(DatabaseSettings.toJdbcUrl(rawUrl));
        dataSource.setUsername(DatabaseSettings.resolveUsername(environment, rawUrl));
        dataSource.setPassword(DatabaseSettings.resolvePassword(environment, rawUrl));
        return dataSource;
    }

    @Bean
    @Conditional(DatabaseUrlCondition.class)
    JdbcTemplate stockAiJdbcTemplate(DataSource stockAiDataSource) {
        return new JdbcTemplate(stockAiDataSource);
    }
}

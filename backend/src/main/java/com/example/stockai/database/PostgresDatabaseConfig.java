package com.example.stockai.database;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
class PostgresDatabaseConfig {
    @Bean
    @Conditional(DatabaseUrlCondition.class)
    DataSource stockAiDataSource(Environment environment) {
        String rawUrl = DatabaseSettings.resolveUrl(environment);
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(DatabaseSettings.toJdbcUrl(rawUrl));
        config.setUsername(DatabaseSettings.resolveUsername(environment, rawUrl));
        config.setPassword(DatabaseSettings.resolvePassword(environment, rawUrl));
        config.setMaximumPoolSize(integerProperty(environment, "stockai.database.maximum-pool-size", 10));
        config.setMinimumIdle(integerProperty(environment, "stockai.database.minimum-idle", 1));
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        config.setPoolName("stock-ai-pool");
        return new HikariDataSource(config);
    }

    @Bean
    @Conditional(DatabaseUrlCondition.class)
    JdbcTemplate stockAiJdbcTemplate(DataSource stockAiDataSource) {
        return new JdbcTemplate(stockAiDataSource);
    }

    private static int integerProperty(Environment environment, String name, int defaultValue) {
        Integer value = environment.getProperty(name, Integer.class);
        return value == null ? defaultValue : Math.max(1, value);
    }
}

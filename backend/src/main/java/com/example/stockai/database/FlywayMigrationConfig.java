package com.example.stockai.database;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(DatabaseUrlCondition.class)
class FlywayMigrationConfig {
    @Bean(initMethod = "migrate")
    Flyway stockAiFlyway(DataSource stockAiDataSource) {
        return Flyway.configure()
            .dataSource(stockAiDataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("1"))
            .validateOnMigrate(true)
            .load();
    }
}

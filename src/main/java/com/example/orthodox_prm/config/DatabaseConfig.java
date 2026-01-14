package com.example.orthodox_prm.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    /**
     * This configuration creates a DataSource using Railway's PostgreSQL environment variables.
     * Railway provides: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD
     * This bean is only created when these environment variables are present (production).
     */
    @Bean
    @ConditionalOnProperty(name = "PGHOST", matchIfMissing = false)
    public DataSource railwayDataSource() {
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String database = System.getenv("PGDATABASE");
        String user = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }
}

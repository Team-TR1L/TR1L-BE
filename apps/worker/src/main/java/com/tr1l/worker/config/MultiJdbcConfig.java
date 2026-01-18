package com.tr1l.worker.config;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class MultiJdbcConfig {

    // ===== MAIN Postgres =====
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mainDataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource mainDataSource(
            @Qualifier("mainDataSourceProperties") DataSourceProperties props
    ) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "mainJdbcTemplate")
    @Primary
    public JdbcTemplate mainJdbcTemplate(@Qualifier("mainDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }


    @Bean(name = "mainNamedJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate mainNamedJdbcTemplate(
            @Qualifier("mainDataSource") DataSource ds
    ) {
        return new NamedParameterJdbcTemplate(ds);
    }

    // ===== TARGET Postgres =====
    @Bean
    @ConfigurationProperties("app.datasource.target")
    public DataSourceProperties targetDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "targetDataSource")
    @ConfigurationProperties("app.datasource.target.hikari")
    public DataSource targetDataSource(
            @Qualifier("targetDataSourceProperties") DataSourceProperties props
    ) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "targetJdbcTemplate")
    public JdbcTemplate targetJdbcTemplate(@Qualifier("targetDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "targetNamedJdbcTemplate")
    public NamedParameterJdbcTemplate targetNamedJdbcTemplate(
            @Qualifier("targetDataSource") DataSource ds
    ) {
        return new NamedParameterJdbcTemplate(ds);
    }
}

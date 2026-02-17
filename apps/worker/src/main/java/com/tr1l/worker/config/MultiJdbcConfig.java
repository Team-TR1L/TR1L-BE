package com.tr1l.worker.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

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
@Slf4j
public class MultiJdbcConfig {
    private static final int FIXED_MAX_POOL_SIZE = 30;
    private static final int FIXED_MIN_IDLE = 10;
    private static final String MAIN_POOL_NAME = "main-pool";
    private static final String TARGET_POOL_NAME = "target-pool";

    // ===== MAIN Postgres =====
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mainHikariDataSource")
    public HikariDataSource mainHikariDataSource(
            @Qualifier("mainDataSourceProperties") DataSourceProperties props
    ) {
        HikariDataSource ds = props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        forceFixedPoolConfig(ds, MAIN_POOL_NAME);
        return ds;
    }

    @Bean(name = "mainDataSource")
    @Primary
    public DataSource mainDataSource(
            @Qualifier("mainHikariDataSource") HikariDataSource hikariDataSource,
            org.springframework.beans.factory.ObjectProvider<QueryExecutionListener> queryExecutionListenerProvider
    ) {
        log.info("main hikari configured poolName={} maxPoolSize={} minIdle={} connectionTimeoutMs={}",
                hikariDataSource.getPoolName(),
                hikariDataSource.getMaximumPoolSize(),
                hikariDataSource.getMinimumIdle(),
                hikariDataSource.getConnectionTimeout());
        ProxyDataSourceBuilder builder = ProxyDataSourceBuilder.create(hikariDataSource)
                .name("main")
                .countQuery();
        QueryExecutionListener listener = queryExecutionListenerProvider.getIfAvailable();
        if (listener != null) {
            builder.listener(listener);
        }
        return builder.build();
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

    @Bean(name = "targetHikariDataSource")
    public HikariDataSource targetHikariDataSource(
            @Qualifier("targetDataSourceProperties") DataSourceProperties props
    ) {
        HikariDataSource ds = props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        forceFixedPoolConfig(ds, TARGET_POOL_NAME);
        return ds;
    }

    @Bean(name = "targetDataSource")
    public DataSource targetDataSource(
            @Qualifier("targetHikariDataSource") HikariDataSource hikariDataSource,
            org.springframework.beans.factory.ObjectProvider<QueryExecutionListener> queryExecutionListenerProvider
    ) {
        log.info("target hikari configured poolName={} maxPoolSize={} minIdle={} connectionTimeoutMs={}",
                hikariDataSource.getPoolName(),
                hikariDataSource.getMaximumPoolSize(),
                hikariDataSource.getMinimumIdle(),
                hikariDataSource.getConnectionTimeout());
        ProxyDataSourceBuilder builder = ProxyDataSourceBuilder.create(hikariDataSource)
                .name("target")
                .countQuery();
        QueryExecutionListener listener = queryExecutionListenerProvider.getIfAvailable();
        if (listener != null) {
            builder.listener(listener);
        }
        return builder.build();
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

    private static void forceFixedPoolConfig(HikariDataSource ds, String poolName) {
        ds.setPoolName(poolName);
        ds.setMaximumPoolSize(FIXED_MAX_POOL_SIZE);
        ds.setMinimumIdle(FIXED_MIN_IDLE);
    }
}

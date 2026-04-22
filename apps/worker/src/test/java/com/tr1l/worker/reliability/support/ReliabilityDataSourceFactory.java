package com.tr1l.worker.reliability.support;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

// 타깃 디비 datasource 생성
public final class ReliabilityDataSourceFactory {

    public DataSource createTargetDataSource(ReliabilityRuntimeConfig config) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.targetJdbcUrl());
        dataSource.setUsername(config.targetJdbcUsername());
        dataSource.setPassword(config.targetJdbcPassword());
        return dataSource;
    }
}

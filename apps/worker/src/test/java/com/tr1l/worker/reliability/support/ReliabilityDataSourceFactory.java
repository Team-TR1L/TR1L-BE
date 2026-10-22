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

    // 데이터셋 분포 검증 전용 main 디비 datasource
    public DataSource createMainDataSource(ReliabilityRuntimeConfig config) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.mainJdbcUrl());
        dataSource.setUsername(config.mainJdbcUsername());
        dataSource.setPassword(config.mainJdbcPassword());
        return dataSource;
    }
}

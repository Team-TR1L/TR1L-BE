package com.tr1l.worker.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class MultiTransactionalConfig {

    /*==========================
    *
    *MultiTransactionalConfig
    *
    * @parm Datasource
    * @return 메인 Postgres Transactional manager
    * @author kimdoyeon
    * @version 1.0.0
    * @date 26. 1. 16.
    *
    ==========================**/
    @Bean(name = "TX-main")
    @Primary
    public PlatformTransactionManager mainTxManager(@Qualifier("mainDataSource")DataSource ds){
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "TX-target")
    public PlatformTransactionManager targerTxManager(@Qualifier("targetDataSource")DataSource ds){
        return new DataSourceTransactionManager(ds);
    }
}

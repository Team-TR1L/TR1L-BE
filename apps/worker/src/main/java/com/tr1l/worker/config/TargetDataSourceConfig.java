package com.tr1l.worker.config;


import liquibase.integration.spring.SpringLiquibase;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class TargetDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.liquibase.target")
    public TargetLiquibaseProps targetLiquibaseProps() {
        return new TargetLiquibaseProps();
    }

    @Bean(name = "targetLiquibase")
    public SpringLiquibase targetLiquibase(
            @Qualifier("targetDataSource") DataSource targetDataSource,
            TargetLiquibaseProps props
    ) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(targetDataSource);

        liquibase.setChangeLog(props.getChangeLog());
        liquibase.setContexts(props.getContexts());
        liquibase.setShouldRun(props.isEnabled());

        return liquibase;
    }

    @Getter
    @Setter
    public static class TargetLiquibaseProps {
        private boolean enabled = true;
        private String changeLog;
        private String contexts;
    }

}

package com.tr1l.worker.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.List;

@Configuration
@Profile("dev")//dev 설정에서만 실행
public class TargetDataSourceConfig {

    @Bean
    public DataSourceScriptDatabaseInitializer targetSchemaInitializer(
            @Qualifier("targetDataSource") DataSource dataSource
    ) {

        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();

        settings.setSchemaLocations(List.of("classpath:schema.sql"));

        settings.setMode(DatabaseInitializationMode.ALWAYS);

        return new DataSourceScriptDatabaseInitializer(dataSource,settings);

    }

}

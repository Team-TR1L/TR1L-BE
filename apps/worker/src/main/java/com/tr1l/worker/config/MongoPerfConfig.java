package com.tr1l.worker.config;

import com.mongodb.MongoClientSettings;
import com.tr1l.worker.config.job.MongoQueryTimingCommandListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;

@Configuration
public class MongoPerfConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimingCustomizer() {
        return (MongoClientSettings.Builder builder) ->
                builder.addCommandListener(new MongoQueryTimingCommandListener(50)); // 50ms 이상 느리면 WARN
    }
}
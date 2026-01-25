package com.tr1l.worker.config;

import com.mongodb.MongoClientSettings;
import com.tr1l.worker.config.job.MongoQueryTimingCommandListener;
import com.tr1l.worker.batch.listener.SqlQueryTimingListener;
import io.micrometer.core.instrument.MeterRegistry;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;

@Configuration
public class MongoPerfConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimingCustomizer(MeterRegistry meterRegistry) {
        return (MongoClientSettings.Builder builder) ->
                builder.addCommandListener(new MongoQueryTimingCommandListener(meterRegistry, 50)); // 50ms 이상 느리면 WARN
    }

    @Bean
    public QueryExecutionListener sqlQueryTimingListener(MeterRegistry meterRegistry) {
        return new SqlQueryTimingListener(meterRegistry);
    }
}

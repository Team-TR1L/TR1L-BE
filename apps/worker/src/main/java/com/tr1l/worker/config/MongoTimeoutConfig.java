package com.tr1l.worker.config;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*==========================
 * MongoDB 가 응답하지 않을 때 worker 가 기다리는 시간을 정한다 (TR1L-180)
 *
 * - URI 의 connectTimeoutMS, socketTimeoutMS, serverSelectionTimeoutMS 보다 나중에 적용되어 이 값이 이긴다
 *==========================*/
@Configuration
public class MongoTimeoutConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeoutCustomizer(MongoTimeoutProperties timeout) {
        return builder -> apply(builder, timeout);
    }

    static void apply(MongoClientSettings.Builder builder, MongoTimeoutProperties timeout) {
        builder.applyToSocketSettings(socket -> socket
                        .connectTimeout(timeout.connect().toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(timeout.read().toMillis(), TimeUnit.MILLISECONDS))
                .applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(timeout.serverSelection().toMillis(), TimeUnit.MILLISECONDS));
    }
}

package com.tr1l.worker.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// MongoDB 가 응답하지 않아도 worker 가 끝없이 기다리지 않는지 (TR1L-180)
class MongoTimeoutConfigTest {

    @Test
    @DisplayName("값을 주지 않으면 읽기 120초, 연결 10초, 서버 선택 30초인지 보기")
    void defaultsShouldNotWaitForever() {
        MongoClientSettings settings = build(MongoClientSettings.builder(), new MongoTimeoutProperties(null, null, null));

        assertEquals(120_000, settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS));
        assertEquals(10_000, settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS));
        assertEquals(30_000, settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("URI 에 socketTimeoutMS=0 이 있어도 설정한 읽기 timeout 이 이기는지 보기")
    void shouldOverrideUriTimeout() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://localhost:27017/tr1l?socketTimeoutMS=0"));

        MongoClientSettings settings = build(builder,
                new MongoTimeoutProperties(Duration.ofSeconds(5), Duration.ofSeconds(45), Duration.ofSeconds(15)));

        assertEquals(45_000, settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS));
        assertEquals(5_000, settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS));
        assertEquals(15_000, settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS));
    }

    private MongoClientSettings build(MongoClientSettings.Builder builder, MongoTimeoutProperties timeout) {
        MongoTimeoutConfig.apply(builder, timeout);
        return builder.build();
    }
}

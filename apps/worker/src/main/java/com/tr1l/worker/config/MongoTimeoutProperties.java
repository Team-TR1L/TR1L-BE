package com.tr1l.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/*==========================
 * MongoDB 연결 timeout (TR1L-180)
 *
 * - 드라이버 기본 읽기 timeout 은 0(무한)이라 MongoDB 가 응답하지 않으면 worker 가 끝나지 않는다
 * - read 는 billing_snapshot bulk upsert 가 가장 느렸던 때(MongoDB 메모리 부족, 약 62초)보다 길게 둔다
 *==========================*/
@ConfigurationProperties(prefix = "app.mongo.timeout")
public record MongoTimeoutProperties(
        Duration connect,
        Duration read,
        Duration serverSelection
) {
    public MongoTimeoutProperties {
        if (connect == null) connect = Duration.ofSeconds(10);
        if (read == null) read = Duration.ofSeconds(120);
        if (serverSelection == null) serverSelection = Duration.ofSeconds(30);
    }
}

package com.tr1l.worker.config.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.time.Duration;

@Configuration
public class S3AsyncClientConfig {

    @Bean(destroyMethod = "close")
    public S3AsyncClient s3AsyncClient(
            @Value("${aws.region:ap-northeast-2}") String region,
            @Value("${aws.s3.http.max-concurrency:32}") int maxConcurrency, //동시에 진행중인 HTTP 연결 수
            @Value("${aws.s3.http.max-pending-acquires:12000}") int maxPendingAcquires // 대기 큐
    ) {
        return S3AsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(maxConcurrency)                  // 동시 요청 상한 :contentReference[oaicite:3]{index=3}
                        .maxPendingConnectionAcquires(maxPendingAcquires) // 초과 시 acquire 실패 :contentReference[oaicite:4]{index=4}
//                        .connectionTimeout(Duration.ofSeconds(5))
//                        .readTimeout(Duration.ofSeconds(60))
//                        .writeTimeout(Duration.ofSeconds(60))
                )
                .build();
    }
}
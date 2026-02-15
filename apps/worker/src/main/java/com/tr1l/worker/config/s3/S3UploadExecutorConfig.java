package com.tr1l.worker.config.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Slf4j
public class S3UploadExecutorConfig {

    @Bean(name = "s3UploadExecutor")
    public Executor s3UploadExecutor(
            @Value("${aws.s3.upload.concurrency:16}") int concurrency,
            @Value("${aws.s3.upload.queue-capacity:5000}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();

        ex.setCorePoolSize(concurrency); // 스레드 수
        ex.setMaxPoolSize(concurrency);  //
        ex.setQueueCapacity(queueCapacity); // 100개
        ex.setThreadNamePrefix("s3-upload-");
        // 큐가 가득 차면 배치 스레드에서 backpressure 처리
        //  ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
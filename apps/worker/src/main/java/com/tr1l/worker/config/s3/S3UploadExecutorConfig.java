package com.tr1l.worker.config.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class S3UploadExecutorConfig {

    @Bean(name = "s3UploadExecutor")
    public Executor s3UploadExecutor(
            @Value("${aws.s3.upload.concurrency:32}") int concurrency,
            @Value("${aws.s3.upload.queue-capacity:5000}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(concurrency);
        ex.setMaxPoolSize(concurrency);
        ex.setQueueCapacity(queueCapacity);
        ex.setThreadNamePrefix("s3-upload-");
        ex.initialize();
        return ex;
    }
}
package com.tr1l.worker.config.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class GzipExecutorConfig {

    @Bean(name = "gzipExecutor")
    public Executor gzipExecutor(
            @Value("${s3.gzip.threads:1}") int threads,
            @Value("${s3.gzip.queue:2000}") int queue
    ) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(threads);
        ex.setMaxPoolSize(threads);
        ex.setQueueCapacity(queue);
        ex.setThreadNamePrefix("gzip-");
        ex.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}

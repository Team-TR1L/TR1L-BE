package com.tr1l.delivery.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(150);
        executor.setQueueCapacity(0);

        // 커스텀 정책: 스레드가 꽉 차면, 자리가 날 때까지 "멈춰서 기다림"
        executor.setRejectedExecutionHandler((r, exec) -> {
            try {
                if (!exec.isShutdown()) {
                    // 여기서 Consumer 스레드가 멈춥니다(Block). 
                    // 워커 스레드 하나가 일을 마치고 자리가 나면 그때 풀립니다.
                    exec.getQueue().put(r);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted", e);
            }
        });

        executor.initialize();
        return executor;
    }
}
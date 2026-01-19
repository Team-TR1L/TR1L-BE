package com.tr1l.dispatchserver.runner;

import com.tr1l.dispatch.application.port.in.DispatchOrchestrationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchTaskRunner implements ApplicationRunner {

    private final DispatchOrchestrationUseCase orchestrationUseCase;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 Server started by Infra Cron. Starting Job...");

        try {
            orchestrationUseCase.orchestrate(Instant.now());
            log.info("✅ Kafka Job Completed Successfully.");
        } catch (Exception e) {
            log.error("❌ Batch Job Failed.", e);
        }

        SpringApplication.exit(applicationContext, () -> 0);
        System.exit(0);
    }
}


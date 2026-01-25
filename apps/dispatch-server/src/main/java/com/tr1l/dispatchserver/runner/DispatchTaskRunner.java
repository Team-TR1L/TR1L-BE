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
        int exitCode = 0;
        try {
            orchestrationUseCase.orchestrate(Instant.now());
        } catch (Exception e) {
            log.error("❌ Batch Job Failed.", e);
            exitCode = 1; // Step Functions가 'Fail'로 인식하도록 1 설정
        } finally {
            terminate(exitCode);
        }
    }

    private void terminate(int exitCode) {
        int finalExitCode = SpringApplication.exit(applicationContext, () -> exitCode);
        System.exit(finalExitCode);
    }
}
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
        // SpringApplication.exit는 등록된 ExitCodeGenerator들을 모아서 최종 코드를 생성합니다.
        // 람다로 넘긴 exitCode가 최종 시스템 종료 코드가 됩니다.
        int finalExitCode = SpringApplication.exit(applicationContext, () -> exitCode);

        log.info("👋 Shutting down Dispatch Server (Exit Code: {})", finalExitCode);

        // JVM 강제 종료. 이 명령어가 실행되면 Kafka Producer의
        // 잔여 메시지가 Flush되고 Bean들이 소멸된 후 프로세스가 끝납니다.
        System.exit(finalExitCode);
    }
}
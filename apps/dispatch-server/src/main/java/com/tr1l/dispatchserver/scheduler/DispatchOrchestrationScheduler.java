package com.tr1l.dispatchserver.scheduler;

import com.tr1l.dispatch.application.port.in.DispatchOrchestrationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DispatchOrchestrationScheduler {

    private final DispatchOrchestrationUseCase orchestrationUseCase;

    @Scheduled(fixedDelayString = "PT2H")
    public void run() {
        orchestrationUseCase.orchestrate(Instant.now());
    }
}


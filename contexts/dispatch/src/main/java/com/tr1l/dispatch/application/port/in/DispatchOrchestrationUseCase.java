package com.tr1l.dispatch.application.port.in;

import org.springframework.stereotype.Component;

import java.time.Instant;

public interface DispatchOrchestrationUseCase {
    void orchestrate(Instant now) throws InterruptedException;
}

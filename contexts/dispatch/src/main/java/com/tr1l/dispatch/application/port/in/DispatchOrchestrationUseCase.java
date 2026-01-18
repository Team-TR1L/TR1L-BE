package com.tr1l.dispatch.application.port.in;

import java.time.Instant;

public interface DispatchOrchestrationUseCase {
    void orchestrate(Instant now);
}

package com.tr1l.billing.application.port.out;

import java.time.Instant;

public interface WorkDocStatusPort {
    void markCalculated(String workId, String snapshotId, Instant now);

    void markFailed(String workId, String errorCode, String errorMessage, Instant now);
}

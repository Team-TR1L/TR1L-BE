package com.tr1l.billing.application.port.out;

import java.time.Instant;
import java.util.List;

public interface WorkDocStatusPort {
    record CalculatedUpdate(String workId, String snapshotId,String billingId) {
    }

    record FailedUpdate(String workId, String errorCode, String errorMessage) {
    }

    void markCalculatedAll(List<CalculatedUpdate> updates, Instant now);
    void markFailedAll(List<FailedUpdate> updates, Instant now);
}

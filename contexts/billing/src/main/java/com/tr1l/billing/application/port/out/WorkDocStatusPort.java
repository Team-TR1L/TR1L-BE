package com.tr1l.billing.application.port.out;

import java.time.Instant;
import java.util.List;

public interface WorkDocStatusPort {
    void markCalculated(String workId, String snapshotId, Instant now);

    void markFailed(String workId, String errorCode, String errorMessage, Instant now);

    default void markCalculatedAll(List<CalculatedUpdate> updates, Instant now) {
        if (updates == null || updates.isEmpty()) return;
        for (CalculatedUpdate u : updates) {
            markCalculated(u.workId(), u.snapshotId(), now);
        }
    }

    default void markFailedAll(List<FailedUpdate> updates, Instant now) {
        if (updates == null || updates.isEmpty()) return;
        for (FailedUpdate u : updates) {
            markFailed(u.workId(), u.errorCode(), u.errorMessage(), now);
        }
    }

    record CalculatedUpdate(String workId, String snapshotId) {}

    record FailedUpdate(String workId, String errorCode, String errorMessage) {}
}

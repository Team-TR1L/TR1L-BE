package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import com.tr1l.billing.application.service.IssueBillingService;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataAccessException;

import java.time.Instant;

//
public class CalculateAndSnapshotWriter implements ItemWriter<WorkToTargetRowProcessor.WorkAndTargetRow> {

    private final IssueBillingService issueBillingService;
    private final WorkDocStatusPort statusPort;

    public CalculateAndSnapshotWriter(IssueBillingService issueBillingService, WorkDocStatusPort statusPort) {
        this.issueBillingService = issueBillingService;
        this.statusPort = statusPort;
    }

    @Override
    public void write(Chunk<? extends WorkToTargetRowProcessor.WorkAndTargetRow> chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        Instant now = Instant.now();

        for (WorkToTargetRowProcessor.WorkAndTargetRow item : chunk) {
            String workId = item.work().id(); // 워커 마다 아이디 부여

            try { // 상태 변경 주의
                if (item.row() == null) {
                    statusPort.markFailed(workId, "BATCH-TARGET-NOT-FOUND", "billing_targets_mv row not found", now);
                    continue;
                }

                // ✅ 도메인 계산 + Mongo billing_snapshot upsert
                //
                var result = issueBillingService.issueAndSave(item.row(), workId);

                // ✅ snapshotId = 결정적 BillingId
                String snapshotId = result.billing().billingId().value().toString();

                statusPort.markCalculated(workId, snapshotId, now);

            } catch (DataAccessException dae) {
                throw dae; // 인프라 장애는 Step fail → 재시작 대상
            } catch (Exception e) {
                statusPort.markFailed(workId, "BATCH-DOMAIN-ERROR", safeMsg(e), now);
            }
        }
    }

    private String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 300 ? m.substring(0, 300) : m;
    }
}
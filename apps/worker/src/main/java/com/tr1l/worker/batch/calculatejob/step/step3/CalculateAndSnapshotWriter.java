package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import com.tr1l.billing.application.service.IssueBillingService;
import com.tr1l.billing.domain.model.aggregate.Billing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataAccessException;

import java.time.Instant;

//
@Slf4j
public class CalculateAndSnapshotWriter implements ItemWriter<CalculateBillingProcessor.Result> {

    private final BillingSnapshotSavePort snapshotSavePort; // 최종 결과 저장 -> ISSUED
    private final WorkDocStatusPort statusPort; // 중간 결과 저장 -> Calculated

    public CalculateAndSnapshotWriter(
            BillingSnapshotSavePort snapshotSavePort,
            WorkDocStatusPort statusPort
    ) {
        this.snapshotSavePort = snapshotSavePort;
        this.statusPort = statusPort;
    }

    @Override
    public void write(Chunk<? extends CalculateBillingProcessor.Result> chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        Instant now = Instant.now();

        for (CalculateBillingProcessor.Result r : chunk) {
            String workId = r.work().id(); // 워커 마다 아이디 부여

            // Process 단계에서 실패 값을 그대로 저장
            if (!r.isSuccess()) {
                statusPort.markFailed(workId, r.errorCode(), r.errorMessage(), now);
                continue;
            }

            try {
                // Mongo snapshot -> Issued 상태로 저장
                Billing billing = r.billing();
                snapshotSavePort.save(billing);

                // Mongo billing_work -> Calculated 상태로 변경
                String snapshotId = billing.billingId().value();
                statusPort.markCalculated(workId, snapshotId, now);
            } catch (DataAccessException dae) {
                throw dae;
            }
        }
    }
}
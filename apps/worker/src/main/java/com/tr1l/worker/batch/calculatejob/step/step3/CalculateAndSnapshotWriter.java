package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import com.tr1l.billing.domain.model.aggregate.Billing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        List<Billing> billings = new ArrayList<>(chunk.size());
        List<WorkDocStatusPort.CalculatedUpdate> calculatedUpdates = new ArrayList<>(chunk.size());
        List<WorkDocStatusPort.FailedUpdate> failedUpdates = new ArrayList<>();

        for (CalculateBillingProcessor.Result r : chunk) {
            String workId = r.work().id(); // 워커 마다 아이디 부여

            if (!r.isSuccess()) {
                failedUpdates.add(new WorkDocStatusPort.FailedUpdate(workId, r.errorCode(), r.errorMessage()));
                continue;
            }

            Billing billing = r.billing();
            billings.add(billing);
            calculatedUpdates.add(new WorkDocStatusPort.CalculatedUpdate(
                    workId,
                    billing.billingId().value()
            ));
        }

        try {
            snapshotSavePort.saveAll(billings);
            statusPort.markCalculatedAll(calculatedUpdates, now);
            statusPort.markFailedAll(failedUpdates, now);
        } catch (DataAccessException dae) {
            throw dae;
        }
    }
}

package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.worker.batch.fault.Job1FaultInjector;
import com.tr1l.worker.batch.fault.Job1FaultPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

//
// [mutant M4] 상태 갱신을 snapshot 저장보다 먼저 한다
// 원본: apps/worker/src/main/java/com/tr1l/worker/batch/calculatejob/step/step3/CalculateAndSnapshotWriter.java
@Slf4j
public class CalculateAndSnapshotWriter implements ItemWriter<CalculateBillingProcessor.Result> {
    static {
        System.err.println("Job1 mutant M4 active: CalculateAndSnapshotWriter");
    }


    private final BillingSnapshotSavePort snapshotSavePort; // 최종 결과 저장 -> ISSUED
    private final WorkDocStatusPort statusPort; // 중간 결과 저장 -> Calculated
    private final Job1FaultInjector faultInjector;

    public CalculateAndSnapshotWriter(
            BillingSnapshotSavePort snapshotSavePort,
            WorkDocStatusPort statusPort,
            Job1FaultInjector faultInjector
    ) {
        this.snapshotSavePort = snapshotSavePort;
        this.statusPort = statusPort;
        this.faultInjector = faultInjector;
    }

    @Override
    public void write(Chunk<? extends CalculateBillingProcessor.Result> chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        Instant now = Instant.now();

        List<Billing> billings = new ArrayList<>(chunk.size());
        List<WorkDocStatusPort.CalculatedUpdate> calculatedUpdates = new ArrayList<>(chunk.size());
        List<WorkDocStatusPort.FailedUpdate> failedUpdates = new ArrayList<>();

        for (CalculateBillingProcessor.Result r : chunk) {
            String workId = r.work().id(); // 예: "2025-12-01:1"

            if (!r.isSuccess()) {
                failedUpdates.add(new WorkDocStatusPort.FailedUpdate(
                        workId,
                        r.errorCode(),
                        r.errorMessage()
                ));
                continue;
            }

            Billing billing = r.billing();
            billings.add(billing);

            // snapshotId를 workId로 고정(권장: 멱등/재시작 안정성)
            String snapshotId = workId;
            String billingId = billing.billingId().value();

            calculatedUpdates.add(new WorkDocStatusPort.CalculatedUpdate(
                    workId,
                    snapshotId,
                    billingId
            ));
        }

        try {
            // 재실행 검증용 장애 지점 P3b, P3c, P3d
            // M4: 상태를 먼저 바꾸고 snapshot 을 나중에 저장한다
            faultInjector.hit(Job1FaultPoint.P3b);
            statusPort.markCalculatedAll(calculatedUpdates, now);
            statusPort.markFailedAll(failedUpdates, now);
            faultInjector.hit(Job1FaultPoint.P3c);
            snapshotSavePort.saveAll(billings);
            faultInjector.hit(Job1FaultPoint.P3d);
        } catch (DataAccessException dae) {
            throw dae;
        }
    }
}

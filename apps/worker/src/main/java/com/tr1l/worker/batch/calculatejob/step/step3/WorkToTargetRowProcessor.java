package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import org.springframework.batch.item.ItemProcessor;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

// ClaimedWorkDoc -> BillingTarget 로드
public class WorkToTargetRowProcessor implements ItemProcessor<WorkDocClaimPort.ClaimedWorkDoc, WorkToTargetRowProcessor.WorkAndTargetRow> {

    private final BillingTargetLoadPort loadPort; //
    private final YearMonth billingMonth; // 년월

    public WorkToTargetRowProcessor(BillingTargetLoadPort loadPort, YearMonth billingMonth) {
        this.loadPort = loadPort;
        this.billingMonth = billingMonth;
    }

    @Override
    public WorkAndTargetRow process(WorkDocClaimPort.ClaimedWorkDoc work) {
        // 1건씩 조회하면 PG 부하가 커짐 → 하지만 Processor는 item 단위라서,
        // 실전에서는 Writer에서 userId를 모아 IN 조회해야됨 -> MVP버전이라 그냥 ㄱ
        Map<Long, BillingTargetRow> map = loadPort.loadByUserIds(billingMonth, List.of(work.userId()));
        BillingTargetRow row = map.get(work.userId());

        return new WorkAndTargetRow(work, row);
    }

    public record WorkAndTargetRow(
            WorkDocClaimPort.ClaimedWorkDoc work,
            BillingTargetRow row
    ) {}
}
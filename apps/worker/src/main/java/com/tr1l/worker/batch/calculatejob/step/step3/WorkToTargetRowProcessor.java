package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

// ClaimedWorkDoc -> BillingTarget 로드
@StepScope
public class WorkToTargetRowProcessor implements ItemProcessor<WorkDocClaimPort.ClaimedWorkDoc, WorkToTargetRowProcessor.WorkAndTargetRow> {

    private final BillingTargetLoadPort loadPort; //

    @Value("#{jobExecutionContext['billingYearMonth']}")
    private String billingYearMonth;

    public WorkToTargetRowProcessor(BillingTargetLoadPort loadPort) {
        this.loadPort = loadPort;
    }

    @Override
    public WorkAndTargetRow process(WorkDocClaimPort.ClaimedWorkDoc work) {
        YearMonth yearMonth=YearMonth.parse(billingYearMonth);
        Map<Long, BillingTargetRow> map = loadPort.loadByUserIds(yearMonth, List.of(work.userId()));
        BillingTargetRow row = map.get(work.userId());

        return new WorkAndTargetRow(work, row);
    }

    public record WorkAndTargetRow(
            WorkDocClaimPort.ClaimedWorkDoc work,
            BillingTargetRow row
    ) {}
}
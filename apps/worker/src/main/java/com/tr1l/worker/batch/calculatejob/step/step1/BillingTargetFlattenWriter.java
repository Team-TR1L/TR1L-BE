package com.tr1l.worker.batch.calculatejob.step.step1;

import com.tr1l.billing.api.usecase.FlattenBillingTargetsUseCase;
import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.worker.batch.fault.Job1FaultInjector;
import com.tr1l.worker.batch.fault.Job1FaultPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@StepScope
public class BillingTargetFlattenWriter implements ItemWriter<BillingTargetBaseRow> {
    private final FlattenBillingTargetsUseCase useCase;
    private final BillingTargetFlatParams params;
    private final Job1FaultInjector faultInjector;

    public BillingTargetFlattenWriter(
            FlattenBillingTargetsUseCase useCase,
            Job1FaultInjector faultInjector,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingYearMonth,
            @Value("#{jobExecutionContext['channelOrder']}") String channelOrder
            ) {
        this.useCase = useCase;
        this.faultInjector = faultInjector;
        this.params = BillingTargetFlatParams.of(billingYearMonth,channelOrder);
    }

    @Override
    public void write(Chunk<? extends BillingTargetBaseRow> chunk) throws Exception {
        useCase.execute((List<BillingTargetBaseRow>) chunk.getItems(), params);
        // 재실행 검증용 장애 지점 P1
        faultInjector.hit(Job1FaultPoint.P1);
    }
}

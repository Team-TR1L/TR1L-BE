package com.tr1l.worker.batch.calculatejob.step.step1;

import com.tr1l.billing.api.usecase.FlattenBillingTargetsUseCase;
import com.tr1l.billing.adapter.out.persistence.model.BillingTargetBaseRow;
import com.tr1l.billing.adapter.out.persistence.model.BillingTargetFlatParams;
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

    public BillingTargetFlattenWriter(
            FlattenBillingTargetsUseCase useCase,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingYearMonth,
            @Value("#{jobExecutionContext['channelOrder']}") String channelOrder
            ) {
        this.useCase = useCase;
        this.params = BillingTargetFlatParams.of(billingYearMonth,channelOrder);
    }

    @Override
    public void write(Chunk<? extends BillingTargetBaseRow> chunk) throws Exception {
        useCase.execute((List<BillingTargetBaseRow>) chunk.getItems(), params);
    }
}

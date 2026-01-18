package com.tr1l.worker.batch.calculatejob.step.step1;

import com.tr1l.billing.api.usecase.FlattenBillingTargetsUseCase;
import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
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

    public BillingTargetFlattenWriter(
            FlattenBillingTargetsUseCase useCase,
            @Value("#{jobParameters['billingYearMonth']}") String yearMonth
    ){
        this.useCase=useCase;
        this.params=BillingTargetFlatParams.of(yearMonth);
    }
    @Override
    public void write(Chunk<? extends BillingTargetBaseRow> chunk) throws Exception {
        useCase.execute((List<BillingTargetBaseRow>) chunk.getItems(),params);
    }
}

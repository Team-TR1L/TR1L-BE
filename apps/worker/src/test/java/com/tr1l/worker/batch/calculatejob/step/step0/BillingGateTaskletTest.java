package com.tr1l.worker.batch.calculatejob.step.step0;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingGateTaskletTest {

    @Test
    void execute_setsNoopAndSkipsGateWhenParamsMissing() throws Exception {
        GateBillingCycleUseCase gateUseCase = mock(GateBillingCycleUseCase.class);
        BillingGateTasklet tasklet = new BillingGateTasklet(gateUseCase);

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepContribution contribution = new StepContribution(stepExecution);
        ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

        ReflectionTestUtils.setField(tasklet, "billingYearMonth", null);
        ReflectionTestUtils.setField(tasklet, "cutoff", null);

        RepeatStatus status = tasklet.execute(contribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getExitStatus().getExitCode()).isEqualTo("NOOP");
        verifyNoInteractions(gateUseCase);
    }

    @Test
    void execute_setsNoopWhenGateDecisionNoop() throws Exception {
        GateBillingCycleUseCase gateUseCase = mock(GateBillingCycleUseCase.class);
        BillingGateTasklet tasklet = new BillingGateTasklet(gateUseCase);

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepContribution contribution = new StepContribution(stepExecution);
        ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

        YearMonth billingMonth = YearMonth.of(2025, 1);
        Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");

        ReflectionTestUtils.setField(tasklet, "billingYearMonth", "2025-01");
        ReflectionTestUtils.setField(tasklet, "cutoff", "2025-01-01T00:00:00Z");

        when(gateUseCase.gate(any()))
                .thenReturn(new GateBillingCycleUseCase.GateResult(
                        billingMonth,
                        cutoff,
                        GateBillingCycleUseCase.Decision.NOOP
                ));

        RepeatStatus status = tasklet.execute(contribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getExitStatus().getExitCode()).isEqualTo("NOOP");

        ArgumentCaptor<GateBillingCycleUseCase.GateCommand> captor =
                ArgumentCaptor.forClass(GateBillingCycleUseCase.GateCommand.class);
        verify(gateUseCase).gate(captor.capture());
        assertThat(captor.getValue().billingMonth()).isEqualTo(billingMonth);
        assertThat(captor.getValue().cutoffAt()).isEqualTo(cutoff);
    }
}

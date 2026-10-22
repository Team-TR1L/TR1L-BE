package com.tr1l.worker.batch.calculatejob.step.step4;

import com.tr1l.billing.application.port.out.BillingCycleFinalizedJdbcPort;
import com.tr1l.worker.batch.fault.Job1FaultInjector;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinalizeBillingCycleTaskletTest {

    @Test
    void execute_failsWithoutFinishingWhenUnfinishedWorkRemains() {
        BillingCycleFinalizedJdbcPort port = mock(BillingCycleFinalizedJdbcPort.class);
        when(port.countUnfinishedWork(LocalDate.of(2026, 1, 1))).thenReturn(1_000L);
        FinalizeBillingCycleTasklet tasklet = tasklet(port);

        assertThatThrownBy(() -> tasklet.execute(contribution(), chunkContext()))
                .isInstanceOf(FinalizeBillingCycleTasklet.UnfinishedBillingWorkException.class)
                .hasMessageContaining("1000 billing_work");
        verify(port, never()).markFinishedIfRunning(any());
    }

    @Test
    void execute_finishesCycleWhenEveryWorkIsDone() throws Exception {
        BillingCycleFinalizedJdbcPort port = mock(BillingCycleFinalizedJdbcPort.class);
        when(port.countUnfinishedWork(LocalDate.of(2026, 1, 1))).thenReturn(0L);
        when(port.markFinishedIfRunning(LocalDate.of(2026, 1, 1))).thenReturn(1);
        FinalizeBillingCycleTasklet tasklet = tasklet(port);
        StepContribution contribution = contribution();

        RepeatStatus status = tasklet.execute(contribution, chunkContext());

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        verify(port).markFinishedIfRunning(LocalDate.of(2026, 1, 1));
    }

    private FinalizeBillingCycleTasklet tasklet(BillingCycleFinalizedJdbcPort port) {
        FinalizeBillingCycleTasklet tasklet =
                new FinalizeBillingCycleTasklet(port, new Job1FaultInjector("", 1, "ONCE"));
        ReflectionTestUtils.setField(tasklet, "billingYearMonth", "2026-01");
        return tasklet;
    }

    private StepContribution contribution() {
        return new StepContribution(MetaDataInstanceFactory.createStepExecution());
    }

    private ChunkContext chunkContext() {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        return new ChunkContext(new StepContext(stepExecution));
    }
}

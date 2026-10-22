package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.WorkDocReleasePort;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReleaseClaimedWorkOnFailureListenerTest {

    @Test
    void afterStep_releasesClaimedWorkOfFailedPartition() {
        WorkDocReleasePort port = mock(WorkDocReleasePort.class);
        StepExecution stepExecution = partitionExecution(BatchStatus.FAILED);

        new ReleaseClaimedWorkOnFailureListener(port).afterStep(stepExecution);

        verify(port).releaseClaimed(eq(LocalDate.of(2026, 1, 1)), eq("worker@host-3"), any());
    }

    @Test
    void afterStep_keepsClaimsWhenPartitionCompleted() {
        WorkDocReleasePort port = mock(WorkDocReleasePort.class);
        StepExecution stepExecution = partitionExecution(BatchStatus.COMPLETED);

        new ReleaseClaimedWorkOnFailureListener(port).afterStep(stepExecution);

        verify(port, never()).releaseClaimed(any(), anyString(), any());
    }

    private StepExecution partitionExecution(BatchStatus status) {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        stepExecution.setStatus(status);
        stepExecution.getExecutionContext().putString("workerId", "worker@host-3");
        stepExecution.getJobExecution().getExecutionContext().putString("billingYearMonth", "2026-01");
        return stepExecution;
    }
}

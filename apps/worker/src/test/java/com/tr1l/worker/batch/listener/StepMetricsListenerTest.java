package com.tr1l.worker.batch.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(OutputCaptureExtension.class)
class StepMetricsListenerTest {

    @Test
    void beforeStep_logsStart(CapturedOutput output) {
        StepMetricsListener listener = new StepMetricsListener();

        JobInstance jobInstance = new JobInstance(10L, "stepJob");
        JobExecution jobExecution = new JobExecution(jobInstance, new JobParameters());
        jobExecution.setId(1000L);
        StepExecution stepExecution = new StepExecution("stepA", jobExecution);
        stepExecution.setId(500L);

        listener.beforeStep(stepExecution);

        assertThat(output.getOut()).contains("STEP START");
        assertThat(output.getOut()).contains("name=stepA");
        assertThat(output.getOut()).contains("jobExecutionId=1000");
    }

    @Test
    void afterStep_logsSummaryAndDuration(CapturedOutput output) {
        StepMetricsListener listener = new StepMetricsListener();

        JobInstance jobInstance = new JobInstance(11L, "summaryJob");
        JobExecution jobExecution = new JobExecution(jobInstance, new JobParameters());
        StepExecution stepExecution = new StepExecution("stepB", jobExecution);
        stepExecution.setId(600L);
        stepExecution.setStatus(BatchStatus.COMPLETED);
        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        stepExecution.setStartTime(LocalDateTime.of(2025, 1, 1, 10, 0, 0));
        stepExecution.setEndTime(LocalDateTime.of(2025, 1, 1, 10, 0, 3));
        stepExecution.setReadCount(10);
        stepExecution.setWriteCount(8);
        stepExecution.setCommitCount(2);
        stepExecution.setRollbackCount(0);
        stepExecution.setReadSkipCount(1);
        stepExecution.setProcessSkipCount(0);
        stepExecution.setWriteSkipCount(1);

        listener.afterStep(stepExecution);

        assertThat(output.getOut()).contains("STEP END");
        assertThat(output.getOut()).contains("name=stepB");
        assertThat(output.getOut()).contains("status=COMPLETED");
        assertThat(output.getOut()).contains("exit=COMPLETED");
        assertThat(output.getOut()).contains("durationMs=3000");
        assertThat(output.getOut()).contains("read=10");
        assertThat(output.getOut()).contains("write=8");
        assertThat(output.getOut()).contains("commit=2");
    }

    @Test
    void afterStep_logsFailures(CapturedOutput output) {
        StepMetricsListener listener = new StepMetricsListener();

        JobInstance jobInstance = new JobInstance(12L, "failJob");
        JobExecution jobExecution = new JobExecution(jobInstance, new JobParameters());
        StepExecution stepExecution = new StepExecution("stepC", jobExecution);
        stepExecution.addFailureException(new IllegalArgumentException("bad"));

        listener.afterStep(stepExecution);

        assertThat(output.getOut()).contains("STEP ERROR");
        assertThat(output.getOut()).contains("name=stepC");
        assertThat(output.getOut()).contains("bad");
    }
}

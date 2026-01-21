package com.tr1l.worker.batch.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BatchLoggingListenerTest {

    @Test
    void beforeJob_logsJobStartAndParams(CapturedOutput output) {
        BatchLoggingListener listener = new BatchLoggingListener();

        JobInstance jobInstance = new JobInstance(1L, "testJob");
        JobParameters params = new JobParametersBuilder()
                .addString("foo", "bar")
                .addLong("n", 1L)
                .toJobParameters();
        JobExecution jobExecution = new JobExecution(jobInstance, params);
        jobExecution.setId(123L);

        listener.beforeJob(jobExecution);

        assertThat(output.getOut()).contains("JOB START");
        assertThat(output.getOut()).contains("name=testJob");
        assertThat(output.getOut()).contains("foo=bar");
    }

    @Test
    void afterJob_logsSummaryAndDuration(CapturedOutput output) {
        BatchLoggingListener listener = new BatchLoggingListener();

        JobInstance jobInstance = new JobInstance(2L, "summaryJob");
        JobExecution jobExecution = new JobExecution(jobInstance, new JobParameters());
        jobExecution.setId(200L);
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);
        jobExecution.setStartTime(LocalDateTime.of(2025, 1, 1, 10, 0, 0));
        jobExecution.setEndTime(LocalDateTime.of(2025, 1, 1, 10, 0, 2));

        listener.afterJob(jobExecution);

        assertThat(output.getOut()).contains("JOB END");
        assertThat(output.getOut()).contains("name=summaryJob");
        assertThat(output.getOut()).contains("status=COMPLETED");
        assertThat(output.getOut()).contains("exit=COMPLETED");
        assertThat(output.getOut()).contains("durationMs=2000");
    }

    @Test
    void afterJob_logsFailures(CapturedOutput output) {
        BatchLoggingListener listener = new BatchLoggingListener();

        JobInstance jobInstance = new JobInstance(3L, "failJob");
        JobExecution jobExecution = new JobExecution(jobInstance, new JobParameters());
        jobExecution.setId(300L);
        jobExecution.addFailureException(new IllegalStateException("boom"));

        listener.afterJob(jobExecution);

        assertThat(output.getOut()).contains("JOB ERROR");
        assertThat(output.getOut()).contains("name=failJob");
        assertThat(output.getOut()).contains("boom");
    }
}

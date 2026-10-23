package com.tr1l.worker;

import com.tr1l.worker.config.BatchJobConfiguration;
import com.tr1l.worker.config.TimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// calculateJob 실행 파라미터와 종료 코드 (TR1L-177)
class CalculateJobBatchRunnerTest {
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final BatchJobConfiguration batchConfiguration = mock(BatchJobConfiguration.class);
    private final Job calculateJob = mock(Job.class);
    private final List<Integer> exitCodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(batchConfiguration.getJobName()).thenReturn("calculateJob");
        when(batchConfiguration.getJob1StartTimeAsInstant()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        when(batchConfiguration.getChannelOrder()).thenReturn("EMAIL,SMS");
        when(applicationContext.getBean("calculateJob", Job.class)).thenReturn(calculateJob);
    }

    @Test
    @DisplayName("BATCH_ADD_RUN_ID 가 없어도 calculateJob 은 실행마다 run.id 를 붙이는지 보기")
    void calculateJobShouldAlwaysAddRunId() throws Exception {
        when(jobLauncher.run(any(), any())).thenReturn(execution(BatchStatus.COMPLETED));

        runner(false).run();

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(), parameters.capture());
        assertThat(parameters.getValue().getString("cutoff")).isEqualTo("2026-02-01T00:00:00Z");
        assertThat(parameters.getValue().getLong("run.id")).isNotNull();
        assertThat(exitCodes).containsExactly(0);
    }

    @Test
    @DisplayName("이미 실행 중이라 시작하지 못하면 종료 코드가 0 이 아닌지 보기")
    void skippedStartShouldNotExitWithZero() throws Exception {
        when(jobLauncher.run(any(), any()))
                .thenThrow(new JobExecutionAlreadyRunningException("A job execution for this job is already running"));

        runner(false).run();

        assertThat(exitCodes).containsExactly(CalculateJobBatchRunner.SKIPPED_EXIT_CODE);
        assertThat(CalculateJobBatchRunner.SKIPPED_EXIT_CODE).isNotZero();
    }

    @Test
    @DisplayName("job 이 실패로 끝나면 종료 코드 1 인지 보기")
    void failedJobShouldExitWithOne() throws Exception {
        when(jobLauncher.run(any(), any())).thenReturn(execution(BatchStatus.FAILED));

        runner(false).run();

        assertThat(exitCodes).containsExactly(1);
    }

    private CalculateJobBatchRunner runner(boolean addRunId) {
        return new CalculateJobBatchRunner(applicationContext, jobLauncher, batchConfiguration,
                new TimeProperties(null, null), true, addRunId, exitCodes::add);
    }

    private JobExecution execution(BatchStatus status) {
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(status);
        return execution;
    }
}

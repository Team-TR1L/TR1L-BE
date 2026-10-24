package com.tr1l.worker.batch.formatjob;

import com.tr1l.worker.batch.formatjob.FormatJobStaleExecutionRecovery.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// formatJob 강제 종료 뒤 남은 실행을 주인이 죽었을 때만 FAILED 로 바꾸는지 (TR1L-181)
class FormatJobStaleExecutionRecoveryTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 1);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-05T03:00:00Z"), ZONE);
    private static final Duration LEASE = Duration.ofHours(1);
    private static final JobParameters PARAMETERS = new JobParametersBuilder()
            .addString("cutoff", "2026-02-05T00:00:00Z").toJobParameters();

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final FakeLease lease = new FakeLease();
    private final FormatJobStaleExecutionRecovery recovery = new FormatJobStaleExecutionRecovery(jobRepository, lease, LEASE, CLOCK);

    @Test
    @DisplayName("남은 실행이 없으면 아무것도 바꾸지 않는지 보기")
    void noRunningExecutionShouldChangeNothing() {
        when(jobRepository.getLastJobExecution("formatJob", PARAMETERS)).thenReturn(execution(BatchStatus.FAILED, now()));

        assertThat(recovery.recover(PARAMETERS, MONTH)).isEqualTo(Result.NONE);
        verify(jobRepository, never()).update(any(JobExecution.class));
        assertThat(lease.renewed).isEmpty();
    }

    @Test
    @DisplayName("format_cycle lease 가 지났으면 lease 를 새로 잡고 남은 실행과 step 을 FAILED 로 바꾸는지 보기")
    void expiredLeaseShouldTakeOverAndMarkFailed() {
        JobExecution left = execution(BatchStatus.STARTED, now());
        StepExecution gate = left.createStepExecution("formatGateStep");
        gate.setStatus(BatchStatus.COMPLETED);
        StepExecution snapshot = left.createStepExecution("billingSnapShotStep");
        snapshot.setStatus(BatchStatus.STARTED);
        when(jobRepository.getLastJobExecution("formatJob", PARAMETERS)).thenReturn(left);
        lease.status = "RUNNING";
        lease.renewResult = 1;

        assertThat(recovery.recover(PARAMETERS, MONTH)).isEqualTo(Result.RECOVERED);
        assertThat(lease.renewed).containsExactly(MONTH);
        assertThat(left.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(snapshot.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(gate.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(jobRepository).update(snapshot);
        verify(jobRepository, never()).update(gate);
        verify(jobRepository).update(left);
    }

    @Test
    @DisplayName("format_cycle lease 가 남아 있으면 남은 실행을 건드리지 않는지 보기")
    void liveLeaseShouldNotTouchExecution() {
        JobExecution left = execution(BatchStatus.STARTED, now());
        when(jobRepository.getLastJobExecution("formatJob", PARAMETERS)).thenReturn(left);
        lease.status = "RUNNING";
        lease.renewResult = 0;

        assertThat(recovery.recover(PARAMETERS, MONTH)).isEqualTo(Result.OWNER_MAY_BE_ALIVE);
        assertThat(left.getStatus()).isEqualTo(BatchStatus.STARTED);
        verify(jobRepository, never()).update(any(JobExecution.class));
    }

    @Test
    @DisplayName("format_cycle 이 FINISHED 면 마감 뒤에 죽은 실행이라 FAILED 로 바꾸는지 보기")
    void finishedCycleShouldMarkFailed() {
        JobExecution left = execution(BatchStatus.STARTED, now());
        when(jobRepository.getLastJobExecution("formatJob", PARAMETERS)).thenReturn(left);
        lease.status = "FINISHED";

        assertThat(recovery.recover(PARAMETERS, MONTH)).isEqualTo(Result.RECOVERED);
        assertThat(left.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(lease.renewed).isEmpty();
    }

    @Test
    @DisplayName("gate claim 전이면 실행이 lease 시간 넘게 갱신되지 않았을 때만 FAILED 로 바꾸는지 보기")
    void beforeClaimShouldWaitForLeaseDuration() {
        lease.status = null;

        JobExecution recent = execution(BatchStatus.STARTED, now().minusMinutes(10));
        when(jobRepository.getLastJobExecution("formatJob", PARAMETERS)).thenReturn(recent);
        assertThat(recovery.recover(PARAMETERS, MONTH)).isEqualTo(Result.OWNER_MAY_BE_ALIVE);
        assertThat(recent.getStatus()).isEqualTo(BatchStatus.STARTED);

        JobExecution old = execution(BatchStatus.STARTED, now().minusHours(2));
        when(jobRepository.getLastJobExecution("formatJob", PARAMETERS)).thenReturn(old);
        assertThat(recovery.recover(PARAMETERS, MONTH)).isEqualTo(Result.RECOVERED);
        assertThat(old.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }

    private static JobExecution execution(BatchStatus status, LocalDateTime lastUpdated) {
        JobExecution execution = new JobExecution(new JobInstance(1L, "formatJob"), 1L, PARAMETERS);
        execution.setStatus(status);
        execution.setStartTime(lastUpdated);
        execution.setLastUpdated(lastUpdated);
        return execution;
    }

    private static class FakeLease implements FormatJobStaleExecutionRecovery.FormatCycleLease {
        String status;
        int renewResult;
        final List<YearMonth> renewed = new ArrayList<>();

        @Override
        public String status(YearMonth billingMonth) {
            return status;
        }

        @Override
        public int renewIfExpired(YearMonth billingMonth, Duration leaseDuration) {
            if (renewResult == 1) renewed.add(billingMonth);
            return renewResult;
        }
    }
}

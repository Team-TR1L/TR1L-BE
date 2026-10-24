package com.tr1l.worker.batch.formatjob;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;

/*==========================
 * formatJob 강제 종료 뒤 남은 실행 정리 (TR1L-181)
 *
 * - worker 가 OOM, ECS task 중지로 죽으면 그 JobExecution 이 STARTED 로 남아 같은 JobInstance 재실행이 막힌다
 * - formatJob 은 reader 가 처리한 마지막 userId 를 ExecutionContext 에 남겨 restart 때 이어서 읽는다
 *   그래서 run.id 로 새로 시작하지 않고, 남은 실행을 FAILED 로 바꿔 같은 JobInstance 로 restart 한다
 * - 주인이 죽었는지는 format_cycle lease 로 본다. lease 가 남아 있으면 다른 worker 가 돌고 있을 수 있어 건드리지 않는다
 *==========================*/
@Slf4j
@Component
public class FormatJobStaleExecutionRecovery {
    static final String JOB_NAME = "formatJob";

    public enum Result {
        // 남은 실행이 없다
        NONE,
        // 남은 실행을 FAILED 로 바꿨다. 같은 파라미터로 다시 돌리면 restart 된다
        RECOVERED,
        // 다른 worker 가 돌고 있을 수 있어 시작하지 않는다
        OWNER_MAY_BE_ALIVE
    }

    private final JobRepository jobRepository;
    private final FormatCycleLease lease;
    private final Duration leaseDuration;
    private final Clock clock;

    @Autowired
    public FormatJobStaleExecutionRecovery(
            JobRepository jobRepository,
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbc,
            @Value("${app.format.lease-seconds:3600}") long leaseSeconds
    ) {
        this(jobRepository, new JdbcFormatCycleLease(targetJdbc), Duration.ofSeconds(leaseSeconds), Clock.systemDefaultZone());
    }

    FormatJobStaleExecutionRecovery(JobRepository jobRepository, FormatCycleLease lease, Duration leaseDuration, Clock clock) {
        this.jobRepository = jobRepository;
        this.lease = lease;
        this.leaseDuration = leaseDuration;
        this.clock = clock;
    }

    public Result recover(JobParameters parameters, YearMonth billingMonth) {
        JobExecution last = jobRepository.getLastJobExecution(JOB_NAME, parameters);
        if (last == null || !last.isRunning()) {
            return Result.NONE;
        }

        if (!takeOver(last, billingMonth)) {
            log.error("formatJob execution {} is still {} and its owner may be alive. billingMonth={}",
                    last.getId(), last.getStatus(), billingMonth);
            return Result.OWNER_MAY_BE_ALIVE;
        }

        BatchStatus leftStatus = last.getStatus();
        markFailed(last);
        log.error("formatJob execution {} was left {} by a dead worker. Marked FAILED to restart. billingMonth={}",
                last.getId(), leftStatus, billingMonth);
        return Result.RECOVERED;
    }

    /* 남은 실행의 주인이 죽었으면 이어받는다
     * - format_cycle RUNNING: lease 가 지났을 때만 lease 를 새로 잡으며 이어받는다. 동시에 둘이 와도 하나만 잡는다
     *   restart 는 끝난 Step0 gate 를 다시 돌지 않아서 lease 를 여기서 잡아야 한다
     * - FINISHED: 청구서 생성은 끝났고 마감 뒤에 죽었다. restart 는 남은 step 만 마무리한다
     * - 그 밖(gate claim 전에 죽음): 실행이 lease 시간 넘게 갱신되지 않았을 때만 죽었다고 본다
     */
    private boolean takeOver(JobExecution last, YearMonth billingMonth) {
        String status = lease.status(billingMonth);
        if ("RUNNING".equals(status)) {
            return lease.renewIfExpired(billingMonth, leaseDuration) == 1;
        }
        if ("FINISHED".equals(status)) {
            return true;
        }
        LocalDateTime lastUpdated = last.getLastUpdated() != null ? last.getLastUpdated() : last.getStartTime();
        return lastUpdated == null || lastUpdated.plus(leaseDuration).isBefore(LocalDateTime.now(clock));
    }

    private void markFailed(JobExecution execution) {
        LocalDateTime now = LocalDateTime.now(clock);
        ExitStatus exitStatus = ExitStatus.FAILED.addExitDescription("worker died while running (TR1L-181)");
        for (StepExecution step : execution.getStepExecutions()) {
            if (step.getStatus().isRunning()) {
                step.setStatus(BatchStatus.FAILED);
                step.setExitStatus(exitStatus);
                step.setEndTime(now);
                jobRepository.update(step);
            }
        }
        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(exitStatus);
        execution.setEndTime(now);
        jobRepository.update(execution);
    }

    interface FormatCycleLease {
        // format_cycle 상태. 행이 없으면 null
        String status(YearMonth billingMonth);

        // RUNNING 이고 lease 가 지났으면 새로 잡고 1 을 돌려준다
        int renewIfExpired(YearMonth billingMonth, Duration leaseDuration);
    }

    static class JdbcFormatCycleLease implements FormatCycleLease {
        private final JdbcTemplate jdbc;

        JdbcFormatCycleLease(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String status(YearMonth billingMonth) {
            return jdbc.query("SELECT status FROM format_cycle WHERE billing_month = ?",
                    rs -> rs.next() ? rs.getString(1) : null, Date.valueOf(billingMonth.atDay(1)));
        }

        @Override
        public int renewIfExpired(YearMonth billingMonth, Duration leaseDuration) {
            return jdbc.update("""
                    UPDATE format_cycle
                    SET lease_until = now() + make_interval(secs => ?)
                    WHERE billing_month = ?
                      AND status = 'RUNNING'
                      AND lease_until < now()
                    """, leaseDuration.toSeconds(), Date.valueOf(billingMonth.atDay(1)));
        }
    }
}

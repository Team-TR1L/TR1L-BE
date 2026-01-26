package com.tr1l.worker.batch.formatjob.step.step0;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@RequiredArgsConstructor
@StepScope
public class FormatGateTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate; // ✅ NamedParameterJdbcTemplate -> JdbcTemplate

    @Value("#{jobExecutionContext['cutoff']}")
    private String cutoff;

    @Value("#{jobExecutionContext['billingYearMonth']}")
    private String billingYearMonth;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        if (cutoff == null || billingYearMonth == null) {
            throw new IllegalArgumentException("jobExecutionContext missing: cutoff or billingYearMonth");
        }

        LocalDate billingMonth = YearMonth.parse(billingYearMonth).atDay(1);
        Instant cutoffAt = Instant.parse(cutoff);

        //
        java.sql.Date bm = java.sql.Date.valueOf(billingMonth);

        // 1) ensure row (멱등)
        jdbcTemplate.update("""
                    INSERT INTO format_cycle(billing_month, cutoff, status, lease_until)
                    VALUES (?, ?, 'READY', NULL)
                    ON CONFLICT (billing_month) DO NOTHING
                """, bm, Timestamp.from(cutoffAt));

        // 2) claim (CAS + Job1 FINISHED + 좀비 회수 + lease 1시간)
        int updated = jdbcTemplate.update("""
                    UPDATE format_cycle fc
                    SET status = 'RUNNING',
                        lease_until = now() + interval '1 hour'
                    WHERE fc.billing_month = ?
                      AND (
                        fc.status IN ('READY','FAILED')
                        OR (fc.status='RUNNING' AND fc.lease_until < now())
                      )
                      AND EXISTS (
                        SELECT 1
                        FROM billing_cycle bc
                        WHERE bc.billing_month = ?
                          AND bc.status = 'FINISHED'
                      )
                """, bm, bm);

        if (updated == 1) {
            log.info("[formatJob][Step0] Gate claim SUCCESS. billingMonth={}, cutoff={}", billingMonth, cutoffAt);
            contribution.setExitStatus(ExitStatus.COMPLETED);
        } else {
            log.info("[formatJob][Step0] Gate claim SKIP(NOOP). billingMonth={}", billingMonth);
            contribution.setExitStatus(new ExitStatus("NOOP"));
        }

        return RepeatStatus.FINISHED;
    }
}

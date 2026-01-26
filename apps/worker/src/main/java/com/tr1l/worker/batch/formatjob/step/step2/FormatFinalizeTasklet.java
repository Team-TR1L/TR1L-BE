package com.tr1l.worker.batch.formatjob.step.step2;

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

import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@RequiredArgsConstructor
@StepScope
public class FormatFinalizeTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobExecutionContext['billingYearMonth']}")
    private String billingYearMonth;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (billingYearMonth == null || billingYearMonth.isBlank()) {
            contribution.setExitStatus(new ExitStatus("NOOP"));
            return RepeatStatus.FINISHED;
        }

        LocalDate billingMonth = YearMonth.parse(billingYearMonth).atDay(1);

        //쿼리 문 다 작업 종료되면 finished 찍는다.
        int updated = jdbcTemplate.update("""
                    UPDATE format_cycle
                    SET status = 'FINISHED',
                        lease_until = NULL
                    WHERE billing_month = ?
                      AND status = 'RUNNING'
                """, billingMonth);

        if (updated == 1) {
            log.info("[formatJob][Step2] Finalize SUCCESS. billingMonth={}", billingMonth);
            contribution.setExitStatus(ExitStatus.COMPLETED);
        } else {
            log.info("[formatJob][Step2] Finalize NOOP. billingMonth={}", billingMonth);
            contribution.setExitStatus(new ExitStatus("NOOP"));
        }

        return RepeatStatus.FINISHED;
    }
}

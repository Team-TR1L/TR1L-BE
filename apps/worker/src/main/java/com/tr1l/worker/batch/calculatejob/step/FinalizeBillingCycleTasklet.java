package com.tr1l.worker.batch.calculatejob.step;

import com.tr1l.billing.application.port.out.BillingCycleFinalizedJdbcPort;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Objects;

import static org.springframework.batch.core.ExitStatus.NOOP;

/**
 ==========================
 *$method$
 * step4 마지막 FINSIHED 찍기
 * @author nonstop
 * @version 1.0.0
 * @date 2026-01-18
 * ========================== */
@Component
@StepScope
@RequiredArgsConstructor
public class FinalizeBillingCycleTasklet implements Tasklet {

    private final BillingCycleFinalizedJdbcPort port;


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 잡 파라미터에서 billingMonth(YYYY-MM)를 조회
        var jobParams = chunkContext.getStepContext().getStepExecution().getJobParameters();

        // 문자열을 YearMonth로 변환 (필수 파라미터)
        YearMonth billingMonth = YearMonth.parse(Objects.requireNonNull(jobParams.getString("billingYearMonth")));

        // RUNNING 상태일 때만 FINISHED로 전환
        int updated = port.markFinishedIfRunning(billingMonth);

        // 실제 업데이트가 없으면 NOOP로 종료
        contribution.setExitStatus(updated == 1 ? ExitStatus.COMPLETED : NOOP);

        return RepeatStatus.FINISHED;
    }
}

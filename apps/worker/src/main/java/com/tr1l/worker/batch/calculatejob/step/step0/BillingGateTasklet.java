package com.tr1l.worker.batch.calculatejob.step.step0;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/*==========================
 * [ 정산 범위 고정 ]
 * Step01 : JobParameter -> Instant 변환 후 Gate 유즈 케이스 호출
 *
 * - FINISHED면 NOOP ExitStatus로 종료
 * - PROCEED면 확정 cutoff를 ExecutionContext에 저장
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 17.]
 * @version 1.0
 *==========================*/

@Component
@RequiredArgsConstructor
@StepScope
public class BillingGateTasklet implements Tasklet {
    private final GateBillingCycleUseCase gateUseCase;
    private final ExitStatus NOOP = new ExitStatus("NOOP");

    private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Value("#{jobExecutionContext['cutoff']}")
    private String cutoff;

    @Value("#{jobExecutionContext['billingYearMonth']}")
    private String billingYearMonth;

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) throws Exception {

        //파라미터가 없으면 Job 종료
        if (billingYearMonth==null || billingYearMonth.isBlank() || cutoff==null || cutoff.isBlank()){
            contribution.setExitStatus(NOOP);
            return RepeatStatus.FINISHED;
        }

        //parameter parsing
        YearMonth billingMonth = YearMonth.parse(billingYearMonth);
        Instant cutoffAt=Instant.parse(cutoff);

        var result = gateUseCase.gate(new GateBillingCycleUseCase.GateCommand(billingMonth,cutoffAt));

        //Gate 결과(billing_cycle 조회) NOOP 인 경우
        if (result.decision() == GateBillingCycleUseCase.Decision.NOOP) {
            //Step 종료 상태를 NOOP으로 변경
            contribution.setExitStatus(NOOP);
            //Tasklet 실행 종료
            return RepeatStatus.FINISHED;
        }

        return RepeatStatus.FINISHED;
    }
}

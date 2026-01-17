package com.tr1l.worker.batch.calculatejob.step;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;

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
public class BillingGateTasklet implements Tasklet {
    private final GateBillingCycleUseCase gateUseCase;
    private final ExitStatus NOOP = new ExitStatus("NOOP");

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var jobParams = chunkContext.getStepContext().getStepExecution().getJobParameters();

        //정산 대상 월
        String billingMonthParam=jobParams.getString("billingYearMonth");
        //cutoff 기준
        String cutoffIso=jobParams.getString("cutoff");

        //파라미터가 없으면 Job 종료
        if (billingMonthParam==null || billingMonthParam.isBlank() || cutoffIso==null || cutoffIso.isBlank()){
            contribution.setExitStatus(NOOP);
            return RepeatStatus.FINISHED;
        }

        //parameter parsing
        YearMonth billingMonth = YearMonth.parse(billingMonthParam);
        Instant cutoffAt=Instant.parse(cutoffIso);

        var result = gateUseCase.gate(new GateBillingCycleUseCase.GateCommand(billingMonth,cutoffAt));

        //Gate 결과(billing_cycle 조회) NOOP 인 경우
        if (result.decision() == GateBillingCycleUseCase.Decision.NOOP) {
            //Step 종료 상태를 NOOP으로 변경
            contribution.setExitStatus(NOOP);
            //Tasklet 실행 종료
            return RepeatStatus.FINISHED;
        }

        //다음 Step으로 파라미터 공유
        ExecutionContext jobCtx=chunkContext.getStepContext()
                .getStepExecution().getJobExecution().getExecutionContext();

        jobCtx.putString("billingYearMonth",billingMonthParam);
        jobCtx.putString("cutoff",cutoffIso);

        return RepeatStatus.FINISHED;
    }
}

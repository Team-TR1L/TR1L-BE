package com.tr1l.worker.batch.calculatejob.step.step4;

import com.tr1l.worker.batch.fault.Job1FaultInjector;
import com.tr1l.worker.batch.fault.Job1FaultPoint;
import com.tr1l.billing.application.port.out.BillingCycleFinalizedJdbcPort;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
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
    private final Job1FaultInjector faultInjector;

    @Value("#{jobExecutionContext['billingYearMonth']}")
    private String billingYearMonth;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 문자열을 YearMonth로 변환 (필수 파라미터)
        YearMonth billingMonth = YearMonth.parse(billingYearMonth);

        // 재실행 검증용 장애 지점 P4
        faultInjector.hit(Job1FaultPoint.P4);

        // 끝나지 않은 work 가 남아 있으면 마감하지 않는다
        // 마감하면 이후 재실행이 NOOP 이 되어 남은 work 를 다시 처리할 수 없다
        long unfinished = port.countUnfinishedWork(billingMonth.atDay(1));
        if (unfinished > 0) {
            throw new UnfinishedBillingWorkException(billingMonth, unfinished);
        }

        // RUNNING 상태일 때만 FINISHED로 전환
        int updated = port.markFinishedIfRunning(billingMonth.atDay(1));

        // 실제 업데이트가 없으면 NOOP로 종료
        contribution.setExitStatus(updated == 1 ? ExitStatus.COMPLETED : NOOP);

        return RepeatStatus.FINISHED;
    }

    // TARGET 또는 PROCESSING 이 남아 billing_cycle 을 마감할 수 없을 때
    public static final class UnfinishedBillingWorkException extends IllegalStateException {
        public UnfinishedBillingWorkException(YearMonth billingMonth, long unfinished) {
            super("billing_cycle " + billingMonth + " cannot be finished: " + unfinished + " billing_work still TARGET or PROCESSING");
        }
    }
}

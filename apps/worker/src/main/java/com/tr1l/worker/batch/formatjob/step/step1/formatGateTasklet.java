package com.tr1l.worker.batch.formatjob.step.step1;

import com.tr1l.worker.batch.formatjob.port.out.FormatGatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.springframework.batch.core.ExitStatus.NOOP;


@Component
@StepScope
@RequiredArgsConstructor
public class formatGateTasklet implements Tasklet {

    private final FormatGatePort formatGatePort;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        // 파라미터값 추출
        Map<String, Object> jobParams = chunkContext.getStepContext().getJobParameters();

        String billingMonthStr = (String) jobParams.get("billingMonth");
        String policyVersion = (String) jobParams.get("policyVersion");
        String policyOrder = (String) jobParams.get("policyOrder");
        String policyIndexStr = (String) jobParams.get("policyIndex");

        int policyIndex = Integer.parseInt(policyIndexStr);

        //yearmonth 에 1일 붙혀서 시작
        YearMonth ym = YearMonth.parse(billingMonthStr);
        LocalDate billingMonthDate = ym.atDay(1);

        // ---- 2) currentChannel 계산 + policyIndex 검증 ----
        String[] channels = policyOrder.split(",");
        if(policyIndex < 0 || policyIndex>=channels.length){
            contribution.setExitStatus(ExitStatus.FAILED);
            throw new IllegalArgumentException(
                    "policyIndex out of range. policyIndex=" + policyIndex + ", policyOrder=" + policyOrder
            );
        }


        String currentChannel = channels[policyIndex].trim();



        // ---- 3) Job1 완료 여부 확인(billing_cycle.status == FINISHED) ----
        String cycleStatus = formatGatePort.findBillingCycleStatus(billingMonthDate);

        // billing_cycle row 자체가 없거나 FINISHED가 아니면 Job2 실행 불가 → NOOP
        if (!"FINISHED".equalsIgnoreCase(cycleStatus)) {
            contribution.setExitStatus(NOOP);
            return RepeatStatus.FINISHED;
        }

        // ---- 4) Job2 format_run 선점(중복 실행 차단 + 파라미터 고정) ----
        var gateResult = formatGatePort.tryStartFormatRun(
                billingMonthDate,
                policyVersion,
                policyOrder,
                policyIndex,
                currentChannel
        );

        // ---- 5) GateResult.Decision -> ExitStatus 매핑 ----
        switch (gateResult.decision()) {
            case STARTED -> contribution.setExitStatus(ExitStatus.COMPLETED);

            // 이미 DONE이거나 이미 RUNNING이면 멱등/중복 실행 시도 → NOOP
            case NOOP_ALREADY_DONE, NOOP_ALREADY_RUNNING -> contribution.setExitStatus(NOOP);

            // 같은 run 키로 다른 파라미터가 들어오면 운영 사고 방지 위해 FAIL FAST
            case FAIL_PARAM_MISMATCH -> {
                contribution.setExitStatus(ExitStatus.FAILED);
                throw new IllegalStateException(
                        "format_run param mismatch. " +
                                "storedStatus=" + gateResult.storedStatus() +
                                ", storedPolicyOrder=" + gateResult.storedPolicyOrder() +
                                ", storedChannelType=" + gateResult.storedChannelType() +
                                ", inputPolicyOrder=" + policyOrder +
                                ", inputChannelType=" + currentChannel
                );
            }

            default -> contribution.setExitStatus(ExitStatus.FAILED);
        }

        return RepeatStatus.FINISHED;
    }
}

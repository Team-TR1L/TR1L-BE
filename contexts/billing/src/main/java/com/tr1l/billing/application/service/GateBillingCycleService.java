package com.tr1l.billing.application.service;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;
import com.tr1l.billing.application.exception.BillingApplicationException;
import com.tr1l.billing.application.port.out.BillingCycleGatePort;
import com.tr1l.billing.application.port.out.BillingTargetMemberPort;
import com.tr1l.billing.error.BillingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GateBillingCycleService implements GateBillingCycleUseCase {
    private final BillingCycleGatePort port;
    private final BillingTargetMemberPort memberPort;


    @Override
    @Transactional(transactionManager = "TX-target")
    public GateResult gate(GateCommand command) {
        //입력값 검증
        if (command == null ||
                command.billingMonth() == null ||
                    command.cutoffAt() == null
        ){
            throw new BillingApplicationException(BillingErrorCode.MISSING_REQUIRED_VALUE_COMMAND);
        }

        //DB cutoff 고정 + status 조회
        var row = port.upsertGateAndReturn(command.billingMonth(),command.cutoffAt());

        //이미 FINISHED 상태인 경우 - 종료
        if ("FINISHED".equals(row.status())){
            return new GateResult(row.billingMonth(),row.cutoffAt(),Decision.NOOP);
        }

        // 정산 월의 첫 실행이면 청구 대상 명단을 고정한다. 재실행은 이 명단으로 청구한다 (TR1L-179)
        // gate 와 같은 target 트랜잭션이라 명단은 전부 들어가거나 하나도 안 들어간다
        memberPort.freezeIfAbsent(row.billingMonth());

        //그 외는 다음 step 진행
        return new GateResult(row.billingMonth(),row.cutoffAt(),Decision.PROCEED);
    }
}

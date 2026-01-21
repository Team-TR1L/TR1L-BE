package com.tr1l.billing.application.service;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;
import com.tr1l.billing.application.exception.BillingApplicationException;
import com.tr1l.billing.application.port.out.BillingCycleGatePort;
import com.tr1l.billing.error.BillingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GateBillingCycleService implements GateBillingCycleUseCase {
    private final BillingCycleGatePort port;


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

        //그 외는 다음 step 진행
        return new GateResult(row.billingMonth(),row.cutoffAt(),Decision.PROCEED);
    }
}

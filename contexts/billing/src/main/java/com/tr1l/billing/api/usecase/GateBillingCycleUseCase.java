package com.tr1l.billing.api.usecase;

import java.time.Instant;
import java.time.YearMonth;
/*==========================
 * Step01 Gate 유즈 케이스
 *
 * 1. billingMonth의 cutoff_at 고정
 * 2. 이미 FINISHED 상태 => NOOP 종료 판단
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 17.]
 * @version 1.0
 *==========================*/
public interface GateBillingCycleUseCase {

    GateResult gate(GateCommand command);

    //입력:어떤 월 정산 + cutoff 기준 시간
    record GateCommand(YearMonth billingMonth, Instant cutoffAt){}

    //출력:DB에 확정된 cutoff + step02 진행하는가
    record GateResult(YearMonth billingMonth, Instant cutoffAt, Decision decision) { }

    /**
     * PROCEED: 다음 step 진행
     * NOOP: 종료
     */
    enum Decision {PROCEED, NOOP}
}

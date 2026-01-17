package com.tr1l.billing.application.port.out;

import java.time.Instant;
import java.time.YearMonth;

/*==========================
 * DB 작업 포트
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 17.]
 * @version 1.0
 *==========================*/
public interface BillingCycleGatePort {

    /* UPSERT Query 실행 -> cutoff/status 반환
     * 처음 실행 -> cutoffAt=인프라단에서 주입한 시간 값
     * 재실행 -> 기존 cutoffAt 유지
     */
    GateRow upsertGateAndReturn(YearMonth billingMonth, Instant cutoffAt);

    record GateRow(YearMonth billingMonth, Instant cutoffAt,String status){}
}

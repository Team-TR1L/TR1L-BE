package com.tr1l.billing.application.port.out;

import java.time.YearMonth;
import java.util.Collection;
import java.util.Set;

/*==========================
 * 정산 월 청구 대상 명단 포트
 *
 * - 정산 월의 첫 실행 때 청구 대상 사용자 명단을 한 번에 고정한다
 * - 재실행은 그 사이 사용자 상태가 바뀌어도 고정한 명단으로 청구한다 (TR1L-179)
 *==========================*/
public interface BillingTargetMemberPort {

    /* 명단이 없으면 지금 청구 대상 사용자로 만들고 넣은 수를 돌려준다
     * 이미 있으면 그대로 두고 0 을 돌려준다
     */
    int freezeIfAbsent(YearMonth billingMonth);

    // 주어진 사용자 중 명단에 있는 사용자
    Set<Long> membersAmong(YearMonth billingMonth, Collection<Long> userIds);
}

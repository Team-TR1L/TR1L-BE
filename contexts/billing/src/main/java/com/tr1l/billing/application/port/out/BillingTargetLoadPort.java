package com.tr1l.billing.application.port.out;

import com.tr1l.billing.adapter.out.persistence.model.BillingTargetRow;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface BillingTargetLoadPort {
    /**
     * MV(billing_targets_mv)에서 userId 목록을 한 번에 조회해서 매핑한다.
     */
    Map<Long, BillingTargetRow> loadByUserIds(YearMonth billingMonth, List<Long> userIds);
}

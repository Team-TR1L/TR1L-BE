package com.tr1l.billing.application.port.out;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;

/**
 *
 ==========================
 *$method$
 * step4에서 사용할 port
 * @author nonstop
 * @version 1.0.0
 * @date $2026-01-28
 * ========================== */
public interface BillingCycleFinalizedJdbcPort {
    int markFinishedIfRunning(LocalDate billingMonthDay
    );
}

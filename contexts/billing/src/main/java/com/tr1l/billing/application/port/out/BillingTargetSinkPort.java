package com.tr1l.billing.application.port.out;

import com.tr1l.billing.application.model.BillingTargetFlatRow;

import java.util.List;

public interface BillingTargetSinkPort {
    void upsertBillingTargets(List<BillingTargetFlatRow> rows);
}

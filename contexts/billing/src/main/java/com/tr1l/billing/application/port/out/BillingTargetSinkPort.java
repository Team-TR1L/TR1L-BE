package com.tr1l.billing.application.port.out;

import com.tr1l.billing.adapter.out.persistence.model.BillingTargetFlatRow;

import java.util.List;

public interface BillingTargetSinkPort {
    void upsertBillingTargets(List<BillingTargetFlatRow> rows);
}

package com.tr1l.billing.application.port.out;

import com.tr1l.billing.domain.model.aggregate.Billing;

public interface BillingSnapshotSavePort {
    void save(Billing billing);
}

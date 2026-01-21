package com.tr1l.billing.application.port.out;

import com.tr1l.billing.domain.model.aggregate.Billing;

// MongDB에 Billing 그래도 넣을거임
public interface BillingSnapshotSavePort {
    void save(Billing billing);
}

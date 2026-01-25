package com.tr1l.billing.application.port.out;

import com.tr1l.billing.domain.model.aggregate.Billing;

import java.util.List;

// MongDB에 Billing 그래도 넣을거임
public interface BillingSnapshotSavePort {
    void save(Billing billing);

    default void saveAll(List<Billing> billings) {
        if (billings == null || billings.isEmpty()) return;
        for (Billing billing : billings) {
            save(billing);
        }
    }
}

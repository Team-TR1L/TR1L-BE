package com.tr1l.billing.application.port.out;

import com.tr1l.billing.domain.model.vo.BillingId;

public interface BillingIdGenerator {
    BillingId nextId();
}

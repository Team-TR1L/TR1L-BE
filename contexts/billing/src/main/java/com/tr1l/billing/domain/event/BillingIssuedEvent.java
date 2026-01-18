package com.tr1l.billing.domain.event;

import java.time.Instant;
import java.time.YearMonth;

public record BillingIssuedEvent(
        String billingId,
        long customerId,
        YearMonth period,
        Instant issuedAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "BILLING_ISSUED";
    }

    @Override
    public String aggregateType() {
        return "Billing";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(billingId);
    }

    @Override
    public Instant occurredAt() {
        return issuedAt;
    }
}

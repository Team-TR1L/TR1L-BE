package com.tr1l.billing.domain.event;

import java.time.Instant;

public interface DomainEvent {
    String eventType();      // 예: "BILLING_ISSUED"
    String aggregateType();  // 예: "Billing"
    String aggregateId();    // 예: "123"
    Instant occurredAt();    // 이벤트 발생 시각
}

package com.tr1l.billing.domain.model.enums;

import lombok.Getter;

@Getter
public enum BillingStatus {
    DRAFT, // 초안 생성, totalAmount = 0 고정
    ISSUED, // 해당 시점에 recipient는 최소 1개 발송수단 보유
    CANCELLED
}

package com.tr1l.billing.domain.model.enums;

public enum DiscountBasis {
    PLAN_LIST_PRICE,                // P
    PLAN_NET_AMOUNT,                // 현재 P(이전 할인 반영 후)
    WELFARE_BASE,                   // 현재 P + M
    SUBTOTAL_BEFORE_BUNDLE          // 룩업 정액(결합)
}

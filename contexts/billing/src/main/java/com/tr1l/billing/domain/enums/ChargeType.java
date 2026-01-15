package com.tr1l.billing.domain.enums;

public enum ChargeType {
    PLAN_MONTHLY_FEE, // 요금제
    ADDON_SUBSCRIPTION_FEE, // 부가서비스 합금액
    ADDITIONAL_USAGE_FEE, // 데이터 추가 사용량
    DEVICE_CHARGE // 기기값 -> 0원
}

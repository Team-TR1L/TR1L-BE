package com.tr1l.billing.adapter.out.persistence.model;

import java.math.BigDecimal;

public record BillingTargetRow(
        String billingMonth,          // "2026-01-01"
        String userName, // "박준희"
        long userId, // userID -> 01.17 추가
        String userBirthDate,         // "1999-03-01"
        String recipientEmailEnc, // 암호화된 이메일
        String recipientPhoneEnc, // 암호화된 폰

        String planName, // 요금제 이름 "5G 프리미엄 에센셜"
        long planMonthlyPrice,        //  32000
        String networkTypeName, // "5G" or "LTE"
        String dataBillingTypeCode,   // DBT-01/02/03
        String dataBillingTypeName, // 데이터 청구 유형에 따른 Info용 String "데이터 1.5GB / 1MB당 0.275원 / 3GB 이상 무제한"
        Long includedDataMb,          // 요금제에 포함된 데이터 무제한-> 0, 3200 -> 3200MB사용가능
        BigDecimal excessChargePerMb,       // 0.275
        long usedDataMb, // 총 데이터 사용량, 무제한일 경우 무시

        boolean hasContract, // 선택약정 유무
        double contractRate, // 0.25
        Integer contractDurationMonths, // 12 or 24

        boolean soldierEligible, // 군인 유무

        boolean welfareEligible, //복지 유무
        String welfareCode,  // 복지 유형 "WLF-01, 02, 03"
        String welfareName, // 표시용 "장애인 할인", "국가유공자할인", "기초생활수급자할인"
        double welfareRate, // 복지 할인  0.3
        Long welfareCapAmount, // 복지 상한선 -> 장애인은 0, 나머지 12000 등

//        boolean bundleEligible, // 결합 유무
//        Long bundleTotalDiscountAmount, // 유저의 결합 할인액
//        String bundleName, // 표시용 결합이름 -> "가족무한사랑"

        String optionsJsonb// 원문 JSONB [{ "name":"디즈니+", "monthlyPrice":9405 }, { "name":"티빙",   "monthlyPrice":4950 }]
) {}


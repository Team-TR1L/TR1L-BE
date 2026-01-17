package com.tr1l.billing.domain.service;

import com.tr1l.billing.domain.model.enums.WelfareType;
import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.domain.model.vo.Money;
import com.tr1l.billing.domain.model.vo.Quantity;
import com.tr1l.billing.domain.model.vo.Rate;
import com.tr1l.billing.error.BillingErrorCode;

import java.util.List;

public record BillingCalculationInput(

        Money planMonthlyPriceP, // 요금제
        Money additionalUsageFeeM, // 추가 데이터 사용량

        boolean hasContract, // 선택 약정 여부
        Rate contractRate, // 선택 약정 할인률 -> 0.25

        boolean soldierEligible, // 군인 여부
        Rate soldierRate, // 군인 할인률

        boolean welfareEligible, // 복지 유무
        WelfareType welfareTypeOrNull, // 복지 타입 -> 장애인, 국가기초수급자 등
        Rate welfareRateOrNull,        // 복지 할인률
        Money welfareCapOrNull, // 할인 상한선(최대 12100원 까지 할인)

        Money bundleDiscountAmount, // 결합 총 할인금액

        // --- JSONB (부가서비스만) ---
        List<AddonLine> addonLines      // 부가서비스
) {
    public BillingCalculationInput {
        if (planMonthlyPriceP == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        if (additionalUsageFeeM == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        if (bundleDiscountAmount == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);

        if (contractRate == null) throw new BillingDomainException(BillingErrorCode.INVALID_RATE);
        if (soldierRate == null) throw new BillingDomainException(BillingErrorCode.INVALID_RATE);


        addonLines = (addonLines == null) ? List.of() : List.copyOf(addonLines);

        if (welfareEligible) {
            if (welfareTypeOrNull == null) throw new BillingDomainException(BillingErrorCode.INVALID_WELFARE);
            if (welfareRateOrNull == null) throw new BillingDomainException(BillingErrorCode.INVALID_RATE);
        }
    }

    /** ✅ 기존 코드 호환용: 부가서비스 합계(=addonLines 합산) */
    public Money addonTotal() {
        Money sum = Money.zero();
        for (AddonLine a : addonLines) {
            sum = sum.plus(a.lineAmount());
        }
        return sum;
    }

    /** ✅ 기존 WelfareDiscountPolicy가 in.welfareRate()를 호출하므로 호환 제공 */
    public Rate welfareRate() {
        if (!welfareEligible || welfareRateOrNull == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_WELFARE);
        }
        return welfareRateOrNull;
    }

    // JSONB 한 항목
    public record AddonLine(
            long addonProductId,
            String addonName,
            Quantity quantity,
            Money unitPrice,
            Money lineAmount
    ) {
        public AddonLine {
            if (addonProductId <= 0) throw new BillingDomainException(BillingErrorCode.INVALID_ADDON);
            if (addonName == null || addonName.isBlank()) throw new BillingDomainException(BillingErrorCode.INVALID_ADDON);
            if (quantity == null) throw new BillingDomainException(BillingErrorCode.INVALID_QUANTITY);
            if (unitPrice == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
            if (lineAmount == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        }
        public Money lineAmount() { return lineAmount; }
    }
}

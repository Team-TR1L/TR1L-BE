package com.tr1l.billing.domain.service;

import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.entity.ChargeLine;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.enums.ChargeType;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.policy.*;

import java.util.Objects;
import java.util.Optional;

public final class BillingCalculator {

    private final ContractDiscountPolicy contractPolicy = new ContractDiscountPolicy();
    private final SoldierDiscountPolicy soldierPolicy = new SoldierDiscountPolicy();
    private final WelfareDiscountPolicy welfarePolicy = new WelfareDiscountPolicy();
//    private final BundleDiscountPolicy bundlePolicy = new BundleDiscountPolicy();

    @FunctionalInterface
    public interface LineIdProvider {
        LineId next();
    }

    /**
     * 도메인 계산 수행: Charge/Discount 라인 구성까지 완료된 Billing(DRAFT)을 만든다.
     * - issue()는 Application에서 호출
     */
    public Billing calculateDraft(
            BillingId billingId,
            CustomerId customerId,
            BillingPeriod period,
            CustomerName customerName,
            CustomerBirthDate customerBirthDate,
            Recipient recipient,
            IdempotencyKey idempotencyKey,
            BillingCalculationInput in,
            LineIdProvider lineIdProvider
    ) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(lineIdProvider);

        Billing billing = Billing.draft(
                billingId,
                customerId,
                period,
                customerName,
                customerBirthDate,
                recipient,
                idempotencyKey
        );

        // =========================
        // 1) Charge Lines
        // =========================
        // 요금제 P
        billing.addChargeLine(new ChargeLine(
                lineIdProvider.next(),
                ChargeType.PLAN_MONTHLY_FEE,
                "요금제 월정액",
                new SourceRef("plan", 1L),
                PricingSnapshot.of(in.planMonthlyPriceP())
        ));

        // 추가사용량 M
        if (!in.additionalUsageFeeM().isZero()) {
            billing.addChargeLine(new ChargeLine(
                    lineIdProvider.next(),
                    ChargeType.ADDITIONAL_USAGE_FEE,
                    "초과 데이터 사용량",
                    new SourceRef("usage", 1L),
                    PricingSnapshot.of(in.additionalUsageFeeM())
            ));
        }

        // 부가서비스 라인별 추가
        if (in.addonLines() != null && !in.addonLines().isEmpty()) {
            long seq = 1L;
            for (BillingCalculationInput.AddonLine a : in.addonLines()) {
                if (a.monthlyPrice().isZero()) continue; // 선택: 0원 라인 스킵

                billing.addChargeLine(new ChargeLine(
                        lineIdProvider.next(),
                        ChargeType.ADDON_SUBSCRIPTION_FEE,
                        "부가서비스 - " + a.addonName(),
                        new SourceRef("addon", seq++),
                        PricingSnapshot.of(a.monthlyPrice())
                ));
            }
        }

        // subtotal 계산용 중간값
        Money planP = in.planMonthlyPriceP();
        Money usageM = in.additionalUsageFeeM();
        Money addon = in.addonTotal(); // 합계는 할인 계산용으로만 사용
        Money subtotal = planP.plus(usageM).plus(addon);

        // =========================
        // 2) Discount Lines (ORDER!)
        // =========================
        Money planAfterPercent = planP;
        Money runningTotal = subtotal;

        // (1) 선택약정
        Optional<DiscountLine> contractOpt = contractPolicy.apply(lineIdProvider.next(), in, planP);
        if (contractOpt.isPresent()) {
            DiscountLine dl = contractOpt.get();
            billing.addDiscountLine(dl);

            Money amt = dl.effectiveDiscountAmount();
            planAfterPercent = planAfterPercent.minusNonNegative(amt);
            runningTotal = runningTotal.minusNonNegative(amt);
        }

        // (2) 군인
        Optional<DiscountLine> soldierOpt = soldierPolicy.apply(lineIdProvider.next(), in, planAfterPercent);
        if (soldierOpt.isPresent()) {
            DiscountLine dl = soldierOpt.get();
            billing.addDiscountLine(dl);

            Money amt = dl.effectiveDiscountAmount();
            planAfterPercent = planAfterPercent.minusNonNegative(amt);
            runningTotal = runningTotal.minusNonNegative(amt);
        }

        // (3) 복지 (base=(planAfterPercent)+M)
        Optional<DiscountLine> welfareOpt = welfarePolicy.apply(lineIdProvider.next(), in, planAfterPercent, usageM);
        if (welfareOpt.isPresent()) {
            DiscountLine dl = welfareOpt.get();
            billing.addDiscountLine(dl);

            Money amt = dl.effectiveDiscountAmount();
            runningTotal = runningTotal.minusNonNegative(amt);
        }

        // (4) 결합(정액) - 기준은 runningTotal
//        Optional<DiscountLine> bundleOpt = bundlePolicy.apply(lineIdProvider.next(), in, runningTotal);
//        bundleOpt.ifPresent(billing::addDiscountLine);

        // =========================
        // 3) Totals
        // =========================
        billing.recalculateTotals();
        return billing;
    }
}


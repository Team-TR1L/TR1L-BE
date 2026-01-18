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
    private final BundleDiscountPolicy bundlePolicy = new BundleDiscountPolicy();

    @FunctionalInterface
    public interface LineIdProvider {
        LineId next();
    }

    /**
     * 도메인 계산 수행: Charge/Discount 라인 구성까지 완료된 Billing(DRAFT)을 만든다.
     * - issue()는 Application에서 호출 (트랜잭션/Outbox/Mongo 저장과 함께)
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
                idempotencyKey);

        // =========================
        // 1) Charge Lines
        // =========================
        // 요금제 P
        billing.addChargeLine(new ChargeLine(
                lineIdProvider.next(),
                ChargeType.PLAN_MONTHLY_FEE,
                "요금제 월정액",
                new SourceRef("plan", 1L),
                PricingSnapshot.of(Quantity.one(), in.planMonthlyPriceP())
        ));

        // 추가사용량 M (없으면 0으로 들어오게 만드는 걸 추천)
        if (!in.additionalUsageFeeM().isZero()) {
            billing.addChargeLine(new ChargeLine(
                    lineIdProvider.next(),
                    ChargeType.ADDITIONAL_USAGE_FEE,
                    "추가사용량",
                    new SourceRef("usage", 1L),
                    PricingSnapshot.of(Quantity.one(), in.additionalUsageFeeM())
            ));
        }

        // 부가서비스 합(라인으로 쪼개고 싶으면 여기서 리스트로 추가하면 됨)
        if (!in.addonTotal().isZero()) {
            billing.addChargeLine(new ChargeLine(
                    lineIdProvider.next(),
                    ChargeType.ADDON_SUBSCRIPTION_FEE,
                    "부가서비스",
                    new SourceRef("addon", 1L),
                    PricingSnapshot.of(Quantity.one(), in.addonTotal())
            ));
        }

        // subtotal 계산은 Aggregate가 하니까, 서비스에서는 “할인 계산용 중간값”만 만든다.
        Money planP = in.planMonthlyPriceP();
        Money usageM = in.additionalUsageFeeM();
        Money addon = in.addonTotal();

        Money subtotal = planP.plus(usageM).plus(addon);

        // =========================
        // 2) Discount Lines (ORDER!)
        // =========================
        Money planAfterPercent = planP;           // %할인 기준이 되는 "요금제 P"의 진행값
        Money runningTotal = subtotal;            // 전체 기준 진행값 (bundle에서 사용)

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

            Money amt = dl.effectiveDiscountAmount(); // cap 반영된 실제 할인액
            runningTotal = runningTotal.minusNonNegative(amt);
        }

// (4) 결합(정액) - 기준은 runningTotal(=결합 전)
        Optional<DiscountLine> bundleOpt = bundlePolicy.apply(lineIdProvider.next(), in, runningTotal);
        bundleOpt.ifPresent(billing::addDiscountLine);

        // =========================
        // 3) Totals
        // =========================
        billing.recalculateTotals();
        return billing;
    }
}

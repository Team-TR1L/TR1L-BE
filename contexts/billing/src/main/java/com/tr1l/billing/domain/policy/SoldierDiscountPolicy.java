package com.tr1l.billing.domain.policy;

import com.tr1l.billing.domain.model.enums.DiscountBasis;
import com.tr1l.billing.domain.model.enums.DiscountMethod;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;

import java.util.Optional;

public final class SoldierDiscountPolicy {

    public Optional<DiscountLine> apply(LineId lineId, BillingCalculationInput in, Money planAfterContract) {
        if (!in.soldierEligible()) {
            return Optional.empty();
        }

        Money base = planAfterContract; // 선약이 없으면 planAfterContract == P
        Money amount = base.multiply(in.soldierRate()); // 할인 금액

        DiscountBasis basis = (in.hasContract())
                ? DiscountBasis.PLAN_NET_AMOUNT //
                : DiscountBasis.PLAN_LIST_PRICE;

        return Optional.of(new DiscountLine(
                lineId,
                DiscountType.SOLDIER,
                DiscountMethod.PERCENT,
                "현역병사 할인",
                new SourceRef("soldier_policy", 20L),
                basis,
                base,
                in.soldierRate(),
                null,
                amount
        ));
    }
}

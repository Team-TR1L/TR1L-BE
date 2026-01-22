package com.tr1l.billing.domain.policy;

import com.tr1l.billing.domain.model.enums.DiscountBasis;
import com.tr1l.billing.domain.model.enums.DiscountMethod;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;

import java.util.Optional;

public final class WelfareDiscountPolicy {

    public Optional<DiscountLine> apply(LineId lineId, BillingCalculationInput in, Money planAfterPercent, Money usageFeeM) {
        if (!in.welfareEligible()) return Optional.empty();

        Money base = planAfterPercent.plus(usageFeeM); // (P after %)+M
        Money raw = base.multiply(in.welfareRate());

        DiscountCap cap = (in.welfareCapOrNull() == null)
                ? null
                : new DiscountCap(in.welfareCapOrNull());

        Money amount = (cap == null) ? raw : raw.min(cap.maxDiscountAmount());

        return Optional.of(new DiscountLine(
                lineId,
                DiscountType.WELFARE,
                DiscountMethod.PERCENT,
                in.welfareName(),
                new SourceRef("welfare_discount", 1L),
                DiscountBasis.WELFARE_BASE,
                base,
                in.welfareRate(),
                cap,
                amount
        ));
    }
}

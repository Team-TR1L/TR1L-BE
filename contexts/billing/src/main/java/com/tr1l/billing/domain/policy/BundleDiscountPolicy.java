package com.tr1l.billing.domain.policy;

import com.tr1l.billing.domain.model.enums.DiscountBasis;
import com.tr1l.billing.domain.model.enums.DiscountMethod;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;

import java.util.Optional;

public final class BundleDiscountPolicy {

    public Optional<DiscountLine> apply(LineId lineId, BillingCalculationInput in, Money baseBeforeBundle) {
        if (in.bundleDiscountAmount() == null || in.bundleDiscountAmount().isZero()) return Optional.empty();

        // 방어: 결합할인이 총액을 넘지 않도록&결합할인 직전 금액 적용
        Money amount = in.bundleDiscountAmount().min(baseBeforeBundle);

        return Optional.of(new DiscountLine(
                lineId,
                DiscountType.BUNDLE_FAMILY,
                DiscountMethod.AMOUNT,
                "가족결합 할인",
                new SourceRef("family_discount_policy", 1L),
                DiscountBasis.SUBTOTAL_BEFORE_BUNDLE,
                baseBeforeBundle, // AMOUNT라도 audit 위해 넣어도 됨(너희 엔티티는 optional)
                null,
                null,
                amount
        ));
    }
}

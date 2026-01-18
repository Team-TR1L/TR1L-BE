package com.tr1l.billing.domain.policy;

import com.tr1l.billing.domain.model.enums.DiscountBasis;
import com.tr1l.billing.domain.model.enums.DiscountMethod;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;

import java.util.Optional;

// 선택 약정
public final class ContractDiscountPolicy {

    public Optional<DiscountLine> apply(LineId lineId, BillingCalculationInput in, Money basePlanP) {
        // 선택 약정이 없는 사용자일 경우
        if (!in.hasContract()) {
            return Optional.empty();
        }

        Money base = basePlanP; // 선약은 '정가 P' 기준
        Money amount = base.multiply(in.contractRate()); // 할인된 금액

        return Optional.of(new DiscountLine(
                lineId,
                DiscountType.CONTRACT_25P,
                DiscountMethod.PERCENT,
                "선택약정 할인",
                new SourceRef("contract_discount", 25L),
                DiscountBasis.PLAN_LIST_PRICE,
                base, // 기준 금액
                in.contractRate(), // 할인율 (0.25)
                null, // cap 없음
                amount // 할인액
        ));
    }
}

package com.tr1l.billing.domain.policy;

import com.tr1l.billing.domain.model.enums.DiscountBasis;
import com.tr1l.billing.domain.model.enums.DiscountMethod;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;

import java.math.RoundingMode;
import java.util.Optional;

// [mutant M6] 선택 약정 할인액을 내림 대신 올림으로 계산한다
// 원본: contexts/billing/.../domain/policy/ContractDiscountPolicy.java
// 선택 약정
public final class ContractDiscountPolicy {
    static {
        System.err.println("Job1 mutant M6 active: ContractDiscountPolicy");
    }


    public Optional<DiscountLine> apply(LineId lineId, BillingCalculationInput in, Money basePlanP) {
        // 선택 약정이 없는 사용자일 경우
        if (!in.hasContract()) {
            return Optional.empty();
        }

        Money base = basePlanP; // 선약은 '정가 P' 기준
        Money amount = new Money(java.math.BigDecimal.valueOf(base.amount())
                .multiply(in.contractRate().value())
                .setScale(0, RoundingMode.CEILING)
                .longValueExact()); // M6: FLOOR -> CEILING

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

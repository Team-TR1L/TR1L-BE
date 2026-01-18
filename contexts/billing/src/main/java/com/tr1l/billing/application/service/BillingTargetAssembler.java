package com.tr1l.billing.application.service;

import com.tr1l.billing.application.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*==========================
 * BaseRow + Facts를 billing_targets 적재용 FlatRow로 조립하는 클래스
 *
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 19.]
 * @version 1.0
 *==========================*/
@Component
public class BillingTargetAssembler {
    public List<BillingTargetFlatRow> assemble(
            List<BillingTargetBaseRow> baseRows,
            BillingTargetFacts facts,
            BillingTargetFlatParams params,
            Map<Long, String> optionsJsonByUser
    ) {
        if (baseRows == null || baseRows.isEmpty()) return List.of();

        Map<Long, Long> usageByUser = facts.usageByUser();
        Map<Long, ContractFact> contractByUser = facts.contractByUser();
        Set<Long> soldierUserIds = facts.soldierUsers();

        List<BillingTargetFlatRow> out = new ArrayList<>(baseRows.size());

        for (BillingTargetBaseRow baseRow : baseRows) {
            long userId = baseRow.userId();
            long usedDataMb = usageByUser.get(userId);

            ContractFact contractFact = contractByUser.get(userId);
            boolean hasContract = contractFact.durationMonths() > 0 && contractFact.discountRate() != null;
            BigDecimal contractRate = contractFact.discountRate();
            int contractDurationMonths = contractFact.durationMonths();

            boolean soldierEligible= soldierUserIds.contains(userId);
            boolean welfareEligible = baseRow.welfareCode() != null; // welfare_code가 존재하면 true
            String welfareCode = baseRow.welfareCode();
            String welfareName = baseRow.welfareName();
            BigDecimal welfareRate = baseRow.welfareRate();
            long welfareCap = baseRow.welfareCapAmount();

            String optionsJson = (optionsJsonByUser != null)
                    ? optionsJsonByUser.getOrDefault(userId, "[]")
                    : "[]";

            out.add(new BillingTargetFlatRow(
                    params.billingMonth(),
                    userId,

                    baseRow.userName(),
                    baseRow.userBirthDate(),
                    baseRow.recipientEmail(),
                    baseRow.recipientPhone(),

                    baseRow.planName(),
                    baseRow.planMonthlyPrice(),
                    baseRow.networkTypeName(),
                    baseRow.dataBillingTypeCode(),
                    baseRow.dataBillingTypeName(),
                    baseRow.includedDataMb(),
                    baseRow.excessChargePerMb(),

                    usedDataMb,

                    hasContract,
                    contractRate,
                    contractDurationMonths,

                    soldierEligible,

                    welfareEligible,
                    welfareCode,
                    welfareName,
                    welfareRate,
                    welfareCap,

                    optionsJson
            ));
        }

        return out;
    }
}

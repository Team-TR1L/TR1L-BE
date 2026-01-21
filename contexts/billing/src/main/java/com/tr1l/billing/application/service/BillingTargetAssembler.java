package com.tr1l.billing.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.application.channel.ChannelPayloadBuilder;
import com.tr1l.billing.application.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
public class BillingTargetAssembler {
    private final ObjectMapper mapper;
    private final ChannelPayloadBuilder builder;

    public List<BillingTargetFlatRow> assemble(
            List<BillingTargetBaseRow> baseRows,
            BillingTargetFacts facts,
            BillingTargetFlatParams params,
            Map<Long, String> optionsJsonByUser
    ) throws JsonProcessingException {
        if (baseRows == null || baseRows.isEmpty()) return List.of();

        Map<Long, Long> usageByUser = facts.usageByUser();
        Map<Long, ContractFact> contractByUser = facts.contractByUser();
        Set<Long> soldierUserIds = facts.soldierUsers();

        List<BillingTargetFlatRow> out = new ArrayList<>(baseRows.size());

        for (BillingTargetBaseRow baseRow : baseRows) {
            long userId = baseRow.userId();

            long usedDataMb = usageByUser.getOrDefault(userId, 0L);

            ContractFact contractFact = contractByUser.get(userId);
            int contractDurationMonths = 0;
            BigDecimal contractRate = BigDecimal.ZERO;

            if (contractFact != null) {
                contractDurationMonths = contractFact.durationMonths();
                contractRate = (contractFact.discountRate() != null) ? contractFact.discountRate() : BigDecimal.ZERO;
            }

            boolean hasContract = contractDurationMonths > 0 && contractRate.compareTo(BigDecimal.ZERO) > 0;

            boolean soldierEligible = soldierUserIds.contains(userId);

            boolean welfareEligible = baseRow.welfareCode() != null;
            String welfareCode = baseRow.welfareCode();
            String welfareName = baseRow.welfareName();
            BigDecimal welfareRate = baseRow.welfareRate();
            long welfareCap = baseRow.welfareCapAmount();

            String fromTime = baseRow.fromTime();
            String toTime = baseRow.toTime();
            String dayTime = baseRow.dayTime();

            if (fromTime != null && fromTime.isBlank()) fromTime = null;
            if (toTime != null && toTime.isBlank()) toTime = null;
            if (dayTime !=null && dayTime.isBlank()) dayTime =null;

            String optionsJson = (optionsJsonByUser != null)
                    ? optionsJsonByUser.getOrDefault(userId, "[]")
                    : "[]";

            //["sms","email"] (String.class) -> List<String> 변환
            log.warn("userId: {} params : {}",userId,params.channelOrder());

            List<String> sendOptionList = mapper.readValue(
                    params.channelOrder(),
                    new TypeReference<List<String>>() {}
            );
            List<ChannelValue> channelOrderJson =
                    builder.build(sendOptionList,new UserContact(baseRow.recipientEmail(),baseRow.recipientPhone()));

            String parsedChannelOrderJson = mapper.writeValueAsString(channelOrderJson);

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
                    optionsJson,
                    fromTime,
                    toTime,
                    dayTime,
                    parsedChannelOrderJson
            ));
        }

        return out;
    }
}

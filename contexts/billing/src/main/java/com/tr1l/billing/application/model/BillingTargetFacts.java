package com.tr1l.billing.application.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record BillingTargetFacts (
        Map<Long,Long> usageByUser,
        Map<Long,ContractFact> contractByUser,
        Set<Long> soldierUsers,
        List<OptionItemRow> optionItems
){
}

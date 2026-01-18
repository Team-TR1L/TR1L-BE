package com.tr1l.billing.application.port.out;

import com.tr1l.billing.application.model.BillingTargetFacts;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.billing.application.model.ContractFact;
import com.tr1l.billing.application.model.OptionItemRow;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*==========================
 * Main DB READ를 실행하는 port
 *
 * main DB - Reference Entity 조회
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 18.]
 * @version 1.0
 *==========================*/
public interface BillingTargetSourcePort {
    BillingTargetFacts fetchFacts(List<Long> userIds, BillingTargetFlatParams params);
}

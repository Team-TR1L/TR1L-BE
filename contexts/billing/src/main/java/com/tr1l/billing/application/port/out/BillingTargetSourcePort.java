package com.tr1l.billing.application.port.out;

import com.tr1l.billing.adapter.out.persistence.model.BillingTargetFacts;
import com.tr1l.billing.adapter.out.persistence.model.BillingTargetFlatParams;

import java.util.List;

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

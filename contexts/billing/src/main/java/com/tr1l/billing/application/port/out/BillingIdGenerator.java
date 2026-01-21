package com.tr1l.billing.application.port.out;

import com.tr1l.billing.domain.model.vo.BillingId;

public interface BillingIdGenerator {
    //  workId 기반으로 BillingId 생성
    BillingId generateForWork(String workId);
}

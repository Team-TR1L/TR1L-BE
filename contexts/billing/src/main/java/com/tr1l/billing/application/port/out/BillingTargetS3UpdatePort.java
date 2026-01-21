package com.tr1l.billing.application.port.out;

import java.time.YearMonth;

public interface BillingTargetS3UpdatePort {

    // billing_targets 테이블의 send_status='READY', s3_url_jsonb= [] 업데이트 역할
    void updateStatus(YearMonth billingMonth, long userId, String s3UrlJsonb);
}

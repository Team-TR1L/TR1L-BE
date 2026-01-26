package com.tr1l.billing.application.port.out;


import java.time.YearMonth;
import java.util.List;

public interface BillingTargetS3UpdatePort {

    // billing_targets 테이블의 send_status='READY', s3_url_jsonb= [] 업데이트 역할
    void updateStatus(YearMonth billingMonth, long userId, String s3UrlJsonb);

    // bulk 연산용 추가
    void updateStatusBulk(List<UpdateRequest> requests);

    // 단일 쿼리용 벌크 연산 01.26
    void updateStatusBulkSingleQuery(List<UpdateRequest> requests);

    record UpdateRequest(YearMonth billingMonth, long userId, String s3UrlJsonb) {}
}

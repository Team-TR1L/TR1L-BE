package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BillingTargetS3UploadAdapter implements BillingTargetS3UpdatePort {

    @Qualifier("targetNamedJdbcTemplate")
    private final NamedParameterJdbcTemplate jdbc;


    // 상태 변경 및 url 저장
    @Override
    public void updateStatus(YearMonth billingMonth, long userId, String s3UrlJsonb) {
        String billingMonthDate = billingMonth.atDay(1).toString(); // YYYY-MM-01 변환

        int updated = jdbc.update("""
            update billing_targets
               set send_status  = 'READY',
                   s3_url_jsonb = cast(:s3UrlJsonb as jsonb)
             where billing_month = :billingMonth
               and user_id       = :userId
        """, Map.of(
                "billingMonth", billingMonthDate,
                "userId", userId,
                "s3UrlJsonb", s3UrlJsonb
        ));
    }
}

package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BillingTargetS3UploadAdapter implements BillingTargetS3UpdatePort {

    // S3 적재 이후 발송 상태를 READY로 전환
    private static final String SQL = """
        UPDATE billing_targets
           SET send_status = 'READY',
               s3_url_jsonb = CAST(:s3UrlJsonb AS jsonb)
         WHERE billing_month = :billingMonth
           AND user_id = :userId
    """;


    private final NamedParameterJdbcTemplate jdbc;

    public BillingTargetS3UploadAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    // 구버전) item단위 상태 변경 및 url 저장
    @Override
    public void updateStatus(YearMonth billingMonth, long userId, String s3UrlJsonb) {
        LocalDate billingMonthDate = billingMonth.atDay(1); // YYYY-MM-01 변환
        log.warn("DB 저장 = {}", s3UrlJsonb);


        jdbc.update("""
                     UPDATE billing_targets
                            SET send_status = 'READY',
                                s3_url_jsonb = CAST(:s3UrlJsonb AS jsonb)
                          WHERE billing_month = :billingMonth
                            AND user_id = :userId
                """, Map.of(
                "billingMonth", billingMonthDate,
                "userId", userId,
                "s3UrlJsonb", s3UrlJsonb
        ));
    }

    /**
     * 01.22 개선
     * 신버전) 벌크 연산으로 최적화 진행
     */
    @Override
    public void updateStatusBulk(List<UpdateRequest> requests) {
       var stream = requests.stream()
               .map(r -> new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                       .addValue("billingMonth", r.billingMonth().atDay(1))
                       .addValue("userId", r.userId())
                       .addValue("s3UrlJsonb", r.s3UrlJsonb()))
               .toArray(org.springframework.jdbc.core.namedparam.SqlParameterSource[]::new);

       jdbc.batchUpdate(SQL, stream);
    }
}

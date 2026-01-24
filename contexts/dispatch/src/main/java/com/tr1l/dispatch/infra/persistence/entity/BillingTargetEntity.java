package com.tr1l.dispatch.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "billing_targets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingTargetEntity {
    @EmbeddedId
    private BillingTargetId id;

    // ===== 전송 제어 관련 =====
    @Column(name = "from_time")
    private String fromTime;

    @Column(name = "to_time")
    private String toTime;

    @Column(name = "day_time") //사용자가 받고 싶은 날짜
    private String dayTime;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "send_status")
    private String sendStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "send_option_jsonb", columnDefinition = "jsonb") // 발송 상태
    private String sendOptionJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "s3_url_jsonb", columnDefinition = "jsonb")// 종착지
    private String s3UrlJsonb;

    public void setSendStatus(String sendStatus) {
        this.sendStatus = sendStatus;
    }
}


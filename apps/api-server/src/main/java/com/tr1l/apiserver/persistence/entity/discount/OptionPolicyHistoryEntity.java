package com.tr1l.apiserver.persistence.entity.discount;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.Instant;

@Entity
@Table(name = "option_policy_history")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Comment("부가 서비스 가격 정책")
public class OptionPolicyHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_history_id", nullable = false, unique = true)
    @Comment("부가 서비스 식별자")
    private Long optionHistoryId;

    @Column(name = "monthly_price", nullable = false)
    @Comment("월 가격")
    @Builder.Default
    private Long monthlyPrice = 0L;

    @Column(name = "effective_from", nullable = false)
    @Comment("적용 시작일")
    private Instant effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    @Comment("적용 종료일")
    private Instant effectiveTo;

    //======== 참조 키 ==========
    @Column(name = "option_service_code",nullable = false,length = 10)
    @Comment("부가 서비스 코드")
    private String optionServiceCode;
}

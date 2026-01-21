package com.tr1l.apiserver.persistence.entity.plan;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

@Builder
@Entity
@Table(name = "plan")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("요금제")
public class PlanEntity {
    @Id
    @Column(name = "plan_code", nullable = false, unique = true, length = 10)
    @Comment("요금제 식별자")
    private String planCode;

    @Column(name = "name", length = 50, nullable = false)
    @Comment("요금제 이름")
    private String name;

    @Column(name = "monthly_price", nullable = false)
    @Comment("월 비용")
    private Integer monthlyPrice;

    @Column(name = "included_data_mb", nullable = false)
    @Comment("기본 데이터 제공량")
    private Integer includedData;

    @Column(name = "excess_charge_per_mb", nullable = false, precision = 5, scale = 4)
    @Comment("초과 데이터 MB당 요금")
    private BigDecimal excessCharge;

    @Column(name = "is_military_eligible", nullable = false)
    @Builder.Default
    @Comment("군인 요금제 여부")
    private Boolean isMilitaryEligible = false;

    @Column(name = "info", nullable = false, columnDefinition = "TEXT")
    @Comment("요금제 설명")
    private String info;

    //======== 참조 키 ==========
    @Column(name = "network_type_code", nullable = false, length = 10)
    @Comment("네트워크 종류 코드")
    private String networkTypeCode;

    @Column(name = "data_billing_type_code",nullable = false,length = 10)
    @Comment("무제한 데이터 코드")
    private String dataBillingTypeCode;
}

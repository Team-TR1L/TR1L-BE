package com.tr1l.apiserver.persistence.entity.discount;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Table(name = "family_discount_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Comment("가족 결합 할인 정책")
public class FamilyDiscountPolicyEntity {
    @Id
    @Column(name = "policy_code", nullable = false, unique = true, length = 20)
    @Comment("가족 결합 정책 식별자")
    private String policyCode;

    @Column(name = "min_user_count", nullable = false)
    @Comment("최소 필요 구성원 수")
    private Integer minUserCount;

    @Column(name = "policy_name", nullable = false, length = 50)
    @Comment("가족 결합 할인 정책 이름")
    private String policyName;

    @Column(name = "min_joined_year_sum", nullable = false)
    @Comment("구성원 합산 가입 년수 시작 값")
    private Integer minJoinedYear;

    @Column(name = "max_joined_year_sum")
    @Comment("구성원 합산 가입 년수 끝 값")
    private Integer maxJoinedYear;

    @Column(name="total_discount_amount",nullable = false)
    @Comment("총 할인 금액")
    private Integer totalDiscountAmount;

    @Column(name ="min_monthly_amount_sum",nullable = false)
    @Comment("가족 구성원 월 이용 총액 기준 시작 값")
    private Integer minMonthlyAmountSum;

    @Column(name ="max_monthly_amount_sum")
    @Comment("가족 구성원 월 이용 총액 기준 끝 값")
    private Integer maxMonthlyAmountSum;

}

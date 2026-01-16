package com.tr1l.apiserver.persistence.entity.plan;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "plan_included_option",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_plan_included_option_plan_option",
                        columnNames = {"plan_code", "option_service_code"}
                )
        })
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Comment("요금제 기본 제공 서비스")
public class PlanIncludedOptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_option_id", nullable = false, unique = true)
    @Comment("요금제 기본 제공 부가 서비스 식별자")
    private Long planOptionId;

    //======== 참조 키 ==========
    @Column(name = "plan_code", nullable = false, length = 10)
    @Comment("요금제 코드 FK")
    private String planCode;

    @Column(name = "option_service_code", nullable = false, length = 10)
    @Comment("부가 서비스 FK")
    private String optionServiceCode;
}

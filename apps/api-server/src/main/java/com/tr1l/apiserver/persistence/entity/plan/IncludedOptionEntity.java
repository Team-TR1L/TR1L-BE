package com.tr1l.apiserver.persistence.entity.plan;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "plan_included_option")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("요금제 기본 제공 부가 서비스")
public class IncludedOptionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_option_id",nullable = false,unique = true)
    @Comment("요금제 기본 제공 부가 서비스 식별자")
    private Long planOptionId;

    //======== 참조 키 ==========
    @Column(name = "plan_code",nullable = false,length = 10)
    @Comment("요금제 FK")
    private String userPlanCode;
}

package com.tr1l.apiserver.persistence.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.Instant;


@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Entity
@Table(name = "user_plans")
@Comment("사용자 요금제")
public class UserPlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_plan_id", nullable = false, unique = true)
    @Comment("사용자 요금제 식별자")
    private Long id;

    @Column(name = "start_date", nullable = false)
    @Comment("요금제 가입일")
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    @Comment("요금제 만료일")
    private Instant endDate;

    //======== 참조 키 ==========
    @Column(name = "user_id",nullable = false)
    @Comment("유저 FK")
    private Long userId;

    @Column(name = "plan_code",nullable = false,length = 10)
    @Comment("요금제 코드")
    private String planCode;

}

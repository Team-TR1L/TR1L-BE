package com.tr1l.apiserver.persistence.entity.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.Instant;

@Entity
@Table(name = "user_option_subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("유저 직접 구독 부가 서비스")
public class UserOptionSubscriptionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_option_id",nullable = false,unique = true)
    @Comment("유저 직접 구독 부가 서비스 식별자")
    private Long userOptionId;

    @Column(name = "start_date",nullable = false)
    @Comment("구독 시작일")
    private Instant startDate;

    @Column(name = "end_date",nullable = false)
    @Comment("구독 만료일")
    private Instant endDate;

     //======== 참조 키 ==========
    @Column(name = "user_id",nullable = false)
    @Comment("유저 FK")
    private Long userId;

    @Column(name = "option_service_code",nullable = false,length = 10)
    @Comment("부가 서비스 코드")
    private String optionServiceCode;

}

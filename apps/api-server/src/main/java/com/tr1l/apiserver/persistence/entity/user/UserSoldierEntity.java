package com.tr1l.apiserver.persistence.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;

@Entity
@Table(name = "user_soldier")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
public class UserSoldierEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_soldier_id",unique = true,nullable = false )
    @Comment("유저군인 식별자")
    private Long userSoldierId;

    @Column(name = "start_date",nullable = false)
    @Comment("입대일")
    private LocalDate startDate;

    @Column(name = "end_date")
    @Comment("전역일")
    private LocalDate endDate;

    //======== 참조 키 ==========
    @Column(name = "user_id",nullable = false,unique = true)
    @Comment("유저 FK")
    private Long userId;

}

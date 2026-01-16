package com.tr1l.apiserver.persistence.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;

@Entity
@Table(name = "user_contract")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("유저 선택 약정")
public class UserContractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_contract_id", nullable = false, unique = true)
    @Comment("유저 선택 약정 식별자")
    private Long userContractId;

    @Column(name = "start_date", nullable = false)
    @Comment("시작일")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    @Comment("종료일")
    private LocalDate endDate;

    //======== 참조 키 ==========
    @Column(name = "user_id", nullable = false)
    @Comment("유저 FK")
    private Long userId;

}

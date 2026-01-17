package com.tr1l.apiserver.persistence.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;

@Entity
@Table(name = "family_group_user",
    uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_family_group_user",
                columnNames = {
                        "user_id",
                        "family_group_id"
                }
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("요금제 기본 제공 부가 서비스")
public class FamilyGroupUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_user_id", nullable = false, unique = true)
    @Comment("가족 구성원 식별자")
    private Long familyUsersId;

    @Column(name = "joined_at", nullable = false)
    @Comment("생성 날짜")
    private LocalDate joinedAt;

    @Column(name = "left_at")
    @Comment("떠난 날짜")
    private LocalDate leftAt;

    //======== 참조 키 ==========
    @Column(name = "user_id",nullable = false)
    private Long userId;

    @Column(name = "family_group_id",nullable = false)
    private Long familyGroupId;
}

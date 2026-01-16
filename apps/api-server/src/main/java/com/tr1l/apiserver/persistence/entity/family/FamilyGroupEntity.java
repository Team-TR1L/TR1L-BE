package com.tr1l.apiserver.persistence.entity.family;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.Instant;

@Builder
@Entity
@Table(name = "family_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("요금제 기본 제공 부가 서비스")
public class FamilyGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_group_id",nullable = false,unique = true)
    @Comment("가족 식별자")
    private Long familyGroupId;

    @Column(name = "status",nullable = false)
    @Builder.Default
    @Comment("가족 상태")
    private Boolean status = false;

    @Column(name = "created_at",nullable = false)
    @Comment("생성 날짜")
    private Instant createdAt;

    @Column(name = "disbanded_at")
    @Comment("해지 날짜")
    private Instant disbandedAt;


}

package com.tr1l.apiserver.persistence.entity.user;

import com.tr1l.apiserver.persistence.enums.UserRole;
import com.tr1l.apiserver.persistence.enums.UserStatus;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("유저")
public class UserEntity {
    @Id
    @Tsid
    @Column(name = "user_id", unique = true, nullable = false)
    @Comment("유저 식별값")
    private Long userId;

    @Column(name = "name", length = 10, nullable = false)
    @Comment("유저 이름")
    private String name;

    @Column(name = "email",length = 50,nullable = false,unique = true)
    private String email;

    @Column(name = "birth_date", nullable = false)
    @Comment("유저 생년월일")
    private LocalDate birthDate;

    @Column(name = "join_date", nullable = false)
    @Comment("유저 가입일")
    private LocalDate joinDate;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    @Comment("유저 전화번호")
    private String phoneNumber;

    @Column(name = "is_welfare",nullable = false)
    @Comment("복지 대상 여부")
    private Boolean isWelfare;

    @Column(name = "user_role", nullable = false)
    @Enumerated(value = EnumType.STRING)
    @Comment("유저 권한")
    private UserRole userRole;

    @Column(name = "user_status", nullable = false)
    @Enumerated(value = EnumType.STRING)
    @Comment("유저 상태")
    private UserStatus userStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("유저 생성일")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    @Comment("유저 수정일")
    private Instant updatedAt;

    //======== 참조 키 ==========
    @Column(name = "welfare_code",length = 10)
    @Comment("복지 할인 코드")
    private String welfareCode;

    @Column(name = "plan_code",length = 10)
    @Comment("요금제 코드")
    private String planCode;

}

package com.tr1l.apiserver.auth;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_auth")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuthEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId; // UserEntity의 userId와 동일

    @Column(nullable = false, unique = true) //UserEntity의 email과 동일
    private String email;

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호
}
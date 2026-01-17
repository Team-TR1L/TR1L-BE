package com.tr1l.apiserver.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
public enum UserRole {
    ADMIN("관리자"),
    GUEST("비회원"),
    USER("일반 유저")
    ;

    @Getter
    private final String name;
}

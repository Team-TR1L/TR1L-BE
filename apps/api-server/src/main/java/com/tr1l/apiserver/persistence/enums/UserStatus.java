package com.tr1l.apiserver.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활동중"),
    WITHDRAWN("탈퇴"),
    BANNED("정지")
    ;

    @Getter
    private final String name;
}

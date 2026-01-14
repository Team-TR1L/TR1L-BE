package com.tr1l.error;

public enum ErrorCategory {
    VAL,//검증
    DOM,//도메인 규칙
    AUTH,//인증 인가
    NOTFOUND,
    ADA,//Adapter 로직
    INFRA,//외부 서비스
    EXT,//외부 연동
    BATCH,
    KAFKA
}

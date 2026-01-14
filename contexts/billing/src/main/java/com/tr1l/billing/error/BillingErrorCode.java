package com.tr1l.billing.error;

import com.tr1l.error.ErrorCategory;
import com.tr1l.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * [BillingErrorCode 규칙]
 * <p>
 * 1) 목적
 * - Billing Bounded Context 에서 발생하는 모든 예외/오류를 ErrorCode 표준화
 * - REST/API 응답, Batch 처리, Kafka 처리, Loki/Grafana 로그에서 동일한 code로 추적 가능해야 한다.
 * <p>
 * 2) code 네이밍 규칙
 * - 포맷: "{BC}-{CATEGORY}-{SEQ}"
 * - BC: Billing = "BIL" (프로젝트 내 BC 약어 통일)
 * - CATEGORY: VAL | DOM | APP | INFRA | EXT 등
 * - SEQ: 3자리 일련번호 (001부터 증가)
 * - 예: "BIL-VAL-001"
 * <p>
 * 3) category 규칙 (ErrorCategory)
 * - VAL   : 입력/데이터 유효성 검증 실패 (형식, 범위, null/empty 등)
 * - DOM   : 도메인 규칙(invariant) 위반 (재시도해도 해결되지 않음)
 * - APP   : 유즈케이스/오케스트레이션 단계 실패 (상태/흐름 문제)
 * - INFRA : DB/네트워크 등 인프라 문제 (재시도 후보)
 * - EXT   : 외부 시스템(S3, 외부 API 등) 연동 문제 (재시도 후보)
 * <p>
 * 4) message 규칙
 * - 사용자/운영 로그에 노출 가능한 "안전한 메시지"만 사용
 * - 이메일/휴대폰 등 PII(민감정보)는 절대 포함하지 않는다.
 * - 상세 원인은 로그의 exception stacktrace / context(MDC)로 추적한다.
 * <p>
 * 5) enum 항목 주석 규칙(필수)
 * - 각 항목 위에 아래 정보를 주석으로 명시한다.
 * - 발생 위치(예: Batch Step, Domain Method, Adapter)
 * - 트리거 조건(어떤 입력/상태에서 발생하는지)
 * - 운영 의도(재시도 의미 여부, 스킵/중단/격리(DLT) 권장)
 */
@RequiredArgsConstructor
public enum BillingErrorCode implements ErrorCode {
    // =====================================================================
    // VAL - Validation
    // =====================================================================

    INVALID_BILLING_AMOUNT("BIL-VAL-001", ErrorCategory.VAL, "합계가 올바르지 않습니다.");


    // =====================================================================
    // DOM - Domain
    // =====================================================================

    // =====================================================================
    // APP - Application
    // =====================================================================

    // =====================================================================
    // ADA - Adapter
    // =====================================================================

    private final String code;
    private final ErrorCategory category;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String message() {
        return message;
    }
}

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

    INVALID_BILLING_AMOUNT("BIL-VAL-001", ErrorCategory.VAL, "합계가 올바르지 않습니다."),
    INVALID_BILLING_ID("BIL-VAL-002", ErrorCategory.VAL, "billingId 값이 올바르지 않습니다."),
    INVALID_CUSTOMER_ID("BIL-VAL-003", ErrorCategory.VAL, "customerId 값이 올바르지 않습니다."),
    INVALID_LINE_ID("BIL-VAL-004", ErrorCategory.VAL, "lineId 값이 올바르지 않습니다."),
    INVALID_IDEMPOTENCY_KEY("BIL-VAL-005", ErrorCategory.VAL, "idempotencyKey 값이 올바르지 않습니다."),
    INVALID_RECIPIENT("BIL-VAL-006", ErrorCategory.VAL, "recipient 값이 올바르지 않습니다."),
    INVALID_ENCRYPTED_EMAIL("BIL-VAL-007", ErrorCategory.VAL, "encryptedEmail 값이 올바르지 않습니다."),
    INVALID_ENCRYPTED_PHONE("BIL-VAL-008", ErrorCategory.VAL, "encryptedPhone 값이 올바르지 않습니다."),
    INVALID_MONEY("BIL-VAL-009", ErrorCategory.VAL, "금액 값이 올바르지 않습니다."),
    INVALID_QUANTITY("BIL-VAL-010", ErrorCategory.VAL, "수량 값이 올바르지 않습니다."),
    INVALID_PRICING_SNAPSHOT("BIL-VAL-011", ErrorCategory.VAL, "가격 스냅샷 값이 올바르지 않습니다."),
    INVALID_BILLING_PERIOD("BIL-VAL-012", ErrorCategory.VAL, "기간이 올바르지 않습니다."),
    INVALID_SOURCE_REF("BIL-VAL-013", ErrorCategory.VAL, "sourceRef 값이 올바르지 않습니다."),
    INVALID_RATE("BIL-VAL-014", ErrorCategory.VAL, "rate 값이 올바르지 않습니다."),
    INVALID_DISCOUNT_CAP("BIL-VAL-015", ErrorCategory.VAL, "discountCap 값이 올바르지 않습니다."),
    INVALID_DISPLAY_NAME("BIL-VAL-016", ErrorCategory.VAL, "displayName 값이 올바르지 않습니다."),
    INVALID_CHARGE_TYPE("BIL-VAL-017", ErrorCategory.VAL, "chargeType 값이 올바르지 않습니다."),
    INVALID_DISCOUNT_TYPE("BIL-VAL-018", ErrorCategory.VAL, "discountType 값이 올바르지 않습니다."),
    INVALID_DISCOUNT_BASIS("BIL-VAL-019", ErrorCategory.VAL, "discountBasis 값이 올바르지 않습니다."),
    INVALID_DISCOUNT_AMOUNT("BIL-VAL-020", ErrorCategory.VAL, "discountAmount 값이 올바르지 않습니다."),
    INVALID_CHARGE_LINE("BIL-VAL-021", ErrorCategory.VAL, "chargeLine 값이 올바르지 않습니다."),
    INVALID_DISCOUNT_LINE("BIL-VAL-022", ErrorCategory.VAL, "discountLine 값이 올바르지 않습니다."),
    INVALID_ISSUED_AT("BIL-VAL-023", ErrorCategory.VAL, "issuedAt 값이 올바르지 않습니다."),
    INVALID_WELFARE("BIL-VAL-024", ErrorCategory.VAL, "welfare 값이 올바르지 않습니다."),
    INVALID_ADDON("BIL-VAL-025", ErrorCategory.VAL, "addon 값이 올바르지 않습니다."),
    INVALID_USER_NAME("BIL-VAL-026", ErrorCategory.VAL, "고객 이름 값이 올바르지 않습니다."),
    INVALID_USER_BIRTH_DATE("BIL-VAL-027", ErrorCategory.VAL, "고객 생년월일 값이 올바르지 않습니다."),
    INVALID_DATA_BILLING_TYPE("BIL-VAL-028", ErrorCategory.VAL, "데이터 과금 타입 값이 올바르지 않습니다."),
    INVALID_INCLUDED_DATA("BIL-VAL-029", ErrorCategory.VAL, "기본 제공 데이터 값이 올바르지 않습니다."),
    INVALID_EXCESS_CHARGE_PER_MB("BIL-VAL-030", ErrorCategory.VAL, "초과 데이터 과금 단가 값이 올바르지 않습니다."),


    // =====================================================================
    // DOM - Domain
    // =====================================================================
    DISCOUNT_CAP_EXCEEDED("BIL-DOM-001", ErrorCategory.DOM, "할인 상한을 초과했습니다."),
    INVALID_DISCOUNT_METHOD("BIL-DOM-002", ErrorCategory.DOM , "할인 정책이 올바르지 않습니다."),
    RATE_NOT_ALLOWED_FOR_AMOUNT_DISCOUNT("BIL-DOM-003", ErrorCategory.DOM , "할인 정책에 따른 할인 method가 올바르지 않습니다." ),
    MISSING_RECIPIENT_CONTACT("BIL-DOM-004", ErrorCategory.DOM, "발행 시점에 수신자 연락처가 필요합니다."),
    BILLING_NOT_MUTABLE("BIL-DOM-005", ErrorCategory.DOM, "발행 이후 청구서는 수정할 수 없습니다.");

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

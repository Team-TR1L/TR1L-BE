package com.tr1l.dispatch.application.exception;

import com.tr1l.error.ErrorCategory;
import com.tr1l.error.ErrorCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DispatchErrorCode implements ErrorCode {
    // =====================================================================
    // VAL - Validation
    // =====================================================================
    DISPATCH_POLICY_ID_NULL("DSP-VAL-001", ErrorCategory.VAL, "DispatchPolicyId는 null일 수 없습니다."),
    CHANNEL_TYPE_NULL("DSP-VAL-002", ErrorCategory.VAL, "ChannelSequence는 null일 수 없습니다."),
    ADMIN_ID_NULL("DSP-VAL-003", ErrorCategory.VAL, "AdminId는 null일 수 없습니다."),
    POLICY_VERSION_NULL("DSP-VAL-004", ErrorCategory.VAL, "PolicyVersion은 null일 수 없습니다."),
    POLICY_VERSION_INVALID("DSP-VAL-005", ErrorCategory.VAL, "PolicyVersion은 1 이상이어야 합니다."),
    POLICY_VERSION_OVERFLOW("DSP-DOM-006", ErrorCategory.DOM, "PolicyVersion이 허용 범위를 초과했습니다."),
    ROUTING_POLICY_NULL("DSP-VAL-006", ErrorCategory.VAL, "RoutingPolicy는 null일 수 없습니다."),
    ENCRYPTED_TEXT_NULL("DSP-VAL-007", ErrorCategory.VAL, "복호화할 암호문이 null이거나 비어있습니다."),


    // =====================================================================
    // DOM - Domain
    // =====================================================================
    POLICY_ALREADY_RETIRED("DSP-DOM-001", ErrorCategory.DOM, "이미 폐기된 정책입니다."),
    POLICY_CANNOT_ACTIVATE("DSP-DOM-002", ErrorCategory.DOM, "현재 상태에서는 정책을 활성화할 수 없습니다."),
    ACTIVE_POLICY_NOT_FOUND("DSP-DOM-003", ErrorCategory.DOM, "현재 활성화 중인 정책이 없습니다."),
    POLICY_NOT_FOUND("DSP-DOM-004", ErrorCategory.DOM, "해당 정책을 찾을 수 없습니다."),

    // =====================================================================
    // APP - Application
    // =====================================================================
    ROUTING_POLICY_SERIALIZATION_FAILED("DSP-APP-001", ErrorCategory.ADA, "RoutingPolicy 직렬화에 실패했습니다."),
    ROUTING_POLICY_DESERIALIZATION_FAILED("DSP-APP-002", ErrorCategory.ADA, "RoutingPolicy 역직렬화에 실패했습니다."),
    MESSAGE_STATUS_ALREADY_PROCESSING("DSP-APP-003",ErrorCategory.ADA, "메시지 상태가 이미 PROCESSING입니다."),
    POLICY_NOT_EQUAL("DSP-APP-004",ErrorCategory.ADA, "발송 정책의 전송 매체와 메시지의 전송 매체가 다릅니다."),
    NO_MORE_RETRY("DSP-APP-005", ErrorCategory.ADA, "발송 정책의 maxAttemptCount보다 높은 attemptCount입니다."),

    // =====================================================================
    // INFRA - Infrastructure (DB, S3, Network)
    // =====================================================================
    S3_DOWNLOAD_FAILED("DSP-INF-001", ErrorCategory.EXT, "S3에서 컨텐츠를 다운로드하는데 실패했습니다."),
    DECRYPTION_FAILED("DSP-INF-002", ErrorCategory.EXT, "데이터 복호화에 실패했습니다.")
    ;

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

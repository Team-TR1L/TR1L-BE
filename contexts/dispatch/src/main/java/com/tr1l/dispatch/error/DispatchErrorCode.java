package com.tr1l.dispatch.error;

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


    // =====================================================================
    // DOM - Domain
    // =====================================================================
    POLICY_ALREADY_RETIRED("DSP-DOM-001", ErrorCategory.DOM, "이미 폐기된 정책입니다."),
    POLICY_CANNOT_ACTIVATE("DSP-DOM-002", ErrorCategory.DOM, "현재 상태에서는 정책을 활성화할 수 없습니다."),
    ACTIVE_POLICY_NOT_FOUND("DSP-DOM-003", ErrorCategory.DOM, "현재 활성화 중인 정책이 없습니다."),

    // =====================================================================
    // APP - Application
    // =====================================================================

    ROUTING_POLICY_SERIALIZATION_FAILED("DSP-APP-001", ErrorCategory.ADA, "RoutingPolicy 직렬화에 실패했습니다."),

    ROUTING_POLICY_DESERIALIZATION_FAILED("DSP-APP-002", ErrorCategory.ADA, "RoutingPolicy 역직렬화에 실패했습니다.")
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

package com.tr1l.billing.application.model;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;

/**
 * 01.21 -> N+1문제 해결 -> reader에서 조회 및 bulk 연산까지 진행
 * @param work
 * @param row
 */
public record WorkAndTargetRow(
        WorkDocClaimPort.ClaimedWorkDoc work,
        BillingTargetRow row
) {
}

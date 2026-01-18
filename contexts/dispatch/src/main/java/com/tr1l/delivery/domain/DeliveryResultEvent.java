package com.tr1l.delivery.domain;

/*
 * 발송 결과 이벤트
 */
public record DeliveryResultEvent(
        Long userId,
        boolean isSuccess
) {}
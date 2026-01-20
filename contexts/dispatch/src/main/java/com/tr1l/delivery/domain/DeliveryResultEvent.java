package com.tr1l.delivery.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;

/*
 * 발송 결과 이벤트
 */
public record DeliveryResultEvent(
        Long userId,
        // JSON 만들 때 isSuccess로 고정
        @JsonProperty("isSuccess") boolean isSuccess,
        LocalDate billingMonth
) {}
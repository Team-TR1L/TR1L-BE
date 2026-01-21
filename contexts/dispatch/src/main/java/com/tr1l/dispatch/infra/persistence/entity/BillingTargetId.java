package com.tr1l.dispatch.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BillingTargetId {

    @Column(name = "billing_month")
    private LocalDate billingMonth;

    @Column(name = "user_id")
    private Long userId;
}


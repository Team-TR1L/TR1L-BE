package com.tr1l.billing.application.model;

/**
 * ==========================
 * RenderBillingMessageQuery
 * Job2의 step1
 * 청구서 만드는 데이터를 유저서비스에 전달하기 위한 메세지쿼리입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import java.util.List;

public record RenderBillingMessageQuery(
        String billingMonth,
        long userId,
        String workId,
        String period,
        String customerName,
        String customerBirthDate,
        String recipientEmail,
        String recipientPhone,
        int subtotalAmount,
        int discountTotalAmount,
        int totalAmount,
        List<Line> chargeLines,
        List<Line> discountLines
) {
    public record Line(String name, int amount) {}
}



package com.tr1l.billing.application.model;

/**
 * ==========================
 * BillingMessage
 * adapter에서 이 데이터들을 받은뒤 청구서들을 만든다.
 * 청구서에필요한 데이터. 마스킹 처리되어있음
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import java.util.List;

public record BillingMessage(
        String period,
        String customerName,
        String email,
        String phone,
        String subtotalAmount,
        String discountTotalAmount,
        String totalAmount,
        List<LineRow> chargeLines,
        List<LineRow> discountLines
) {
    public record LineRow(String name, String amount){}
}



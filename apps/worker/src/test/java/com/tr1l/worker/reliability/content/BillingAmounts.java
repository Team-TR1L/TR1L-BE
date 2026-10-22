package com.tr1l.worker.reliability.content;

import java.util.LinkedHashMap;
import java.util.Map;

// 사용자 한 명의 청구 금액 항목
// 참조 계산과 snapshot 양쪽을 같은 모양으로 맞춘다
public record BillingAmounts(
        long subtotal,
        long discountTotal,
        long total,
        long contractDiscount,
        long soldierDiscount,
        long welfareDiscount
) {
    public Map<String, Long> fields() {
        Map<String, Long> fields = new LinkedHashMap<>();
        fields.put("subtotal", subtotal);
        fields.put("discountTotal", discountTotal);
        fields.put("total", total);
        fields.put("contractDiscount", contractDiscount);
        fields.put("soldierDiscount", soldierDiscount);
        fields.put("welfareDiscount", welfareDiscount);
        return fields;
    }
}

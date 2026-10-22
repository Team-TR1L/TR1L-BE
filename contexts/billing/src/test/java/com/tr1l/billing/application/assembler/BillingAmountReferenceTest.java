package com.tr1l.billing.application.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.model.vo.BillingId;
import com.tr1l.billing.domain.model.vo.CustomerId;
import com.tr1l.billing.domain.model.vo.IdempotencyKey;
import com.tr1l.billing.domain.model.vo.LineId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

// 청구 금액 계산이 C6 참조 계산과 같은지 사용자 단위로 본다
// 입력과 기대 금액은 infra/reliability/scripts/make_reference_cases.py 가 참조 SQL 로 만든 것이다
//   D1 에서 요금 계산에 영향을 주는 값 조합마다 한 명, 기본 제공량과 복지 할인 상한 경계 행
// 운영 계산 코드를 쓰지 않고 만든 기대값이라 PIT 로 계산 코드를 깨뜨렸을 때 잡는지 볼 수 있다
class BillingAmountReferenceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CASES = "/reference/billing-amount-cases.json";

    private final BillingTargetRowAssembler assembler = new BillingTargetRowAssembler(MAPPER);

    static Stream<Arguments> cases() throws IOException {
        try (InputStream in = BillingAmountReferenceTest.class.getResourceAsStream(CASES)) {
            JsonNode root = MAPPER.readTree(in);
            List<Arguments> cases = new ArrayList<>();
            for (JsonNode node : root.get("cases")) {
                BillingTargetRow row = MAPPER.treeToValue(node.get("row"), BillingTargetRow.class);
                cases.add(Arguments.of(row.userId(), row, MAPPER.treeToValue(node.get("expected"), Expected.class)));
            }
            return cases.stream();
        }
    }

    @ParameterizedTest(name = "user {0}")
    @MethodSource("cases")
    @DisplayName("청구 금액이 참조 계산과 항목별로 같은지 보기")
    void amountShouldMatchReference(long userId, BillingTargetRow row, Expected expected) {
        AtomicLong lineSeq = new AtomicLong(1);
        Billing billing = assembler.toDraftBilling(
                new BillingId("reference-" + userId),
                new CustomerId(userId),
                new IdempotencyKey("2026-01-01:" + userId),
                row,
                () -> new LineId(lineSeq.getAndIncrement())
        );

        assertAll(
                () -> assertEquals(expected.subtotal(), billing.subtotalAmount().amount(), "subtotal"),
                () -> assertEquals(expected.contract_discount(), discount(billing, DiscountType.CONTRACT_25P), "contract"),
                () -> assertEquals(expected.soldier_discount(), discount(billing, DiscountType.SOLDIER), "soldier"),
                () -> assertEquals(expected.welfare_discount(), discount(billing, DiscountType.WELFARE), "welfare"),
                () -> assertEquals(expected.discount_total(), billing.discountTotalAmount().amount(), "discount total"),
                () -> assertEquals(expected.total(), billing.totalAmount().amount(), "total")
        );
    }

    private long discount(Billing billing, DiscountType type) {
        return billing.discountLines().stream()
                .filter(line -> line.type() == type)
                .mapToLong(line -> line.effectiveDiscountAmount().amount())
                .sum();
    }

    // 참조 SQL 의 열 이름을 그대로 쓴다
    record Expected(long subtotal, long discount_total, long total,
                    long contract_discount, long soldier_discount, long welfare_discount) {
    }
}

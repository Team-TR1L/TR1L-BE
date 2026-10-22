package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.content.BillingAmounts;
import com.tr1l.worker.reliability.content.ReferenceAmountComparator;
import com.tr1l.worker.reliability.content.ReferenceAmountComparison;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 참조 금액 비교 규칙 고정
// DB 없이 비교기와 참조 SQL 리소스만 본다
class Job1ReferenceAmountContractTest {
    private final ReferenceAmountComparator comparator = new ReferenceAmountComparator();
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();

    @Test
    @DisplayName("항목별 불일치와 누락, 추가 snapshot을 따로 세는지 보기")
    void comparatorShouldCountMismatchByField() {
        Map<Long, BillingAmounts> expected = Map.of(
                1L, new BillingAmounts(58135, 13750, 44385, 13750, 0, 0),
                2L, new BillingAmounts(49540, 0, 49540, 0, 0, 0),
                3L, new BillingAmounts(37000, 0, 37000, 0, 0, 0)
        );
        Map<Long, BillingAmounts> actual = Map.of(
                1L, new BillingAmounts(58135, 13750, 44385, 13750, 0, 0),
                2L, new BillingAmounts(49540, 1, 49539, 0, 0, 1),
                4L, new BillingAmounts(10000, 0, 10000, 0, 0, 0)
        );

        ReferenceAmountComparison comparison = comparator.compare("S-001", expected, actual);

        assertThat(comparison.matchedUsers()).isEqualTo(1);
        assertThat(comparison.mismatchedUsers()).isEqualTo(1);
        assertThat(comparison.missingSnapshots()).isEqualTo(1);
        assertThat(comparison.unexpectedSnapshots()).isEqualTo(1);
        assertThat(comparison.mismatchByField())
                .containsEntry("discountTotal", 1L)
                .containsEntry("total", 1L)
                .containsEntry("welfareDiscount", 1L)
                .doesNotContainKey("subtotal");
        assertThat(comparison.sampleDifferences())
                .extracting(ReferenceAmountComparison.Difference::userId)
                .containsOnly(2L);
        assertThat(comparison.matched()).isFalse();
        assertThat(comparison.violationCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("참조 SQL이 할인 순서와 내림 규칙을 담고 있는지 보기")
    void referenceSqlShouldDescribeDiscountRules() {
        String sql = catalog.loadCheckText("reliability/checks/reference/job1-billing-amount.sql");

        assertThat(sql)
                .contains("FROM billing_targets")
                .contains(":billingMonthDay")
                .contains("floor(plan * contract_rate)")
                .contains("* 0.20")
                .contains("welfare_cap_amount")
                .contains("AS subtotal")
                .contains("AS discount_total")
                .contains("AS total");
        assertThat(sql.indexOf("contract_discount")).isLessThan(sql.indexOf("soldier_discount"));
        assertThat(sql.indexOf("soldier_discount")).isLessThan(sql.indexOf("welfare_discount"));
    }
}

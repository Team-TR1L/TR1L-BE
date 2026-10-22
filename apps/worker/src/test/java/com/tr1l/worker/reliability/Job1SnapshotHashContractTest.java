package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.content.SnapshotCanonicalizer;
import com.tr1l.worker.reliability.content.SnapshotHashComparator;
import com.tr1l.worker.reliability.content.SnapshotHashComparison;
import com.tr1l.worker.reliability.content.SnapshotHashes;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// snapshot 해시 규칙 고정
// DB 없이 정규화와 비교만 본다
class Job1SnapshotHashContractTest {
    private final SnapshotCanonicalizer canonicalizer = new SnapshotCanonicalizer();
    private final SnapshotHashComparator comparator = new SnapshotHashComparator();

    @Test
    @DisplayName("실행 시각 필드와 키 순서가 달라도 해시가 같은지 보기")
    void hashShouldIgnoreVolatileFieldsAndKeyOrder() {
        Document first = snapshot(1L, 49540L)
                .append("createdAt", new Date(1_000L))
                .append("issuedAt", new Date(2_000L))
                .append("updatedAt", new Date(3_000L));
        Document second = new Document("updatedAt", new Date(9_000L))
                .append("payload", new Document("totalAmount", new Document("value", 49540L))
                        .append("chargeLines", List.of(new Document("name", "5G 미니"))))
                .append("workId", "2026-01-01:1")
                .append("userId", 1L)
                .append("billingMonth", "2026-01-01")
                .append("issuedAt", new Date(8_000L));

        assertThat(canonicalizer.sha256(first)).isEqualTo(canonicalizer.sha256(second));
        assertThat(canonicalizer.canonicalJson(first)).doesNotContain("createdAt", "issuedAt", "updatedAt");
    }

    @Test
    @DisplayName("금액이나 청구 항목 순서가 바뀌면 해시가 달라지는지 보기")
    void hashShouldChangeWhenContentChanges() {
        Document base = snapshot(1L, 49540L);
        Document amountChanged = snapshot(1L, 49541L);
        Document reordered = new Document("workId", "2026-01-01:1")
                .append("userId", 1L)
                .append("billingMonth", "2026-01-01")
                .append("payload", new Document("totalAmount", new Document("value", 49540L))
                        .append("chargeLines", List.of(new Document("name", "부가서비스"), new Document("name", "5G 미니"))));
        Document withOption = new Document(base);
        withOption.put("payload", new Document("totalAmount", new Document("value", 49540L))
                .append("chargeLines", List.of(new Document("name", "5G 미니"), new Document("name", "부가서비스"))));

        assertThat(canonicalizer.sha256(base)).isNotEqualTo(canonicalizer.sha256(amountChanged));
        assertThat(canonicalizer.sha256(reordered)).isNotEqualTo(canonicalizer.sha256(withOption));
    }

    @Test
    @DisplayName("일치, 불일치, 누락, 추가, 중복을 따로 세는지 보기")
    void comparatorShouldCountEachDifferenceKind() {
        SnapshotHashes reference = new SnapshotHashes("2026-01-01",
                Map.of(1L, "a", 2L, "b", 3L, "c"), Map.of());
        SnapshotHashes current = new SnapshotHashes("2026-01-01",
                Map.of(1L, "a", 2L, "x", 4L, "d"), Map.of(4L, 2));

        SnapshotHashComparison comparison = comparator.compare("S-001R", "S-001", reference, current);

        assertThat(comparison.hashMatch()).isEqualTo(1);
        assertThat(comparison.hashMismatch()).isEqualTo(1);
        assertThat(comparison.missingInCurrent()).isEqualTo(1);
        assertThat(comparison.extraInCurrent()).isEqualTo(1);
        assertThat(comparison.duplicateUsers()).isEqualTo(1);
        assertThat(comparison.sampleMismatchUserIds()).containsExactly(2L);
        assertThat(comparison.sampleMissingUserIds()).containsExactly(3L);
        assertThat(comparison.sampleExtraUserIds()).containsExactly(4L);
        assertThat(comparison.matched()).isFalse();
        assertThat(comparison.violationCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 해시 묶음이면 일치로 판정하는지 보기")
    void comparatorShouldMatchIdenticalHashes() {
        SnapshotHashes hashes = new SnapshotHashes("2026-01-01", Map.of(1L, "a", 2L, "b"), Map.of());

        SnapshotHashComparison comparison = comparator.compare("S-003R", "S-001", hashes, hashes);

        assertThat(comparison.matched()).isTrue();
        assertThat(comparison.hashMatch()).isEqualTo(2);
        assertThat(comparison.violationCount()).isZero();
    }

    private Document snapshot(long userId, long totalAmount) {
        return new Document("workId", "2026-01-01:" + userId)
                .append("userId", userId)
                .append("billingMonth", "2026-01-01")
                .append("payload", new Document("totalAmount", new Document("value", totalAmount))
                        .append("chargeLines", List.of(new Document("name", "5G 미니"))));
    }
}

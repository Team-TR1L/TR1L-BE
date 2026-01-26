package com.tr1l.worker.batch.formatjob.step.step1;

import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingSnapshotKeysetReaderTest {

    @Test
    void read_usesKeysetAndUpdatesExecutionContext() {
        // MongoTemplate은 실제 DB 호출 대신 쿼리 생성 여부만 검증하기 위해 mock 사용.
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        BillingSnapshotKeysetReader reader = new BillingSnapshotKeysetReader(
                mongoTemplate,
                "billing_snapshot",
                "2026-01",
                2
        );

        // ExecutionContext에 이전 처리 지점(lastUserId)을 세팅해 재시작 상황을 가정.
        ExecutionContext executionContext = new ExecutionContext();
        executionContext.putLong("billingSnapshot.lastUserId", 5L);
        reader.open(executionContext);

        // First page returned by Mongo (keyset should start after userId=5).
        // 첫 페이지: lastUserId(5) 이후인 10, 20을 반환하도록 구성.
        List<BillingSnapshotDoc> firstPage = List.of(
                doc(10L),
                doc(20L)
        );

        // Second call returns empty to stop the reader.
        // 두 번째 호출은 빈 결과로 만들어 더 이상 읽을 데이터가 없음을 시뮬레이션.
        when(mongoTemplate.find(any(Query.class), eq(BillingSnapshotDoc.class), eq("billing_snapshot")))
                .thenReturn(firstPage)
                .thenReturn(List.of());

        // read는 한 건씩 반환하므로 두 번 읽어서 10, 20이 나오는지 확인.
        BillingSnapshotDoc first = reader.read();
        BillingSnapshotDoc second = reader.read();
        // 세 번째 호출은 기억된 페이지가 소진되고 다음 페이지가 비어있으므로 null 반환.
        BillingSnapshotDoc third = reader.read();

        assertThat(first.userId()).isEqualTo(10L);
        assertThat(second.userId()).isEqualTo(20L);
        assertThat(third).isNull();

        reader.update(executionContext);
        // update 시점에 lastUserId가 마지막으로 읽은 userId(20)로 저장되는지 검증.
        assertThat(executionContext.getLong("billingSnapshot.lastUserId")).isEqualTo(20L);

        // Capture queries to verify keyset boundary moves forward.
        // 실제 DB 호출에 전달된 Query를 캡처해 keyset 조건이 올바른지 검증.
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(2))
                .find(queryCaptor.capture(), eq(BillingSnapshotDoc.class), eq("billing_snapshot"));

        // 첫 번째 쿼리는 billingMonth=2026-01-01, userId > 5 이어야 함.
        Document firstQuery = queryCaptor.getAllValues().get(0).getQueryObject();
        // 두 번째 쿼리는 lastUserId가 20으로 갱신된 이후 userId > 20 이어야 함.
        Document secondQuery = queryCaptor.getAllValues().get(1).getQueryObject();

        assertThat(firstQuery.getString("billingMonth")).isEqualTo("2026-01-01");
        assertThat(((Number) ((Document) firstQuery.get("userId")).get("$gt")).longValue()).isEqualTo(5L);

        assertThat(secondQuery.getString("billingMonth")).isEqualTo("2026-01-01");
        assertThat(((Number) ((Document) secondQuery.get("userId")).get("$gt")).longValue()).isEqualTo(20L);
    }

    private static BillingSnapshotDoc doc(long userId) {
        return new BillingSnapshotDoc(
                "id-" + userId,
                "2026-01-01",
                userId,
                "READY",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                "hihi"
        );
    }
}

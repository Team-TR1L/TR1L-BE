package com.tr1l.billing.adapter.out.persistence.mongo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.domain.model.aggregate.Billing;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MongoBillingSnapshotSaveAdapter implements BillingSnapshotSavePort {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final String collectionName;

    public MongoBillingSnapshotSaveAdapter(
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            @Value("${app.billing.snapshot.collection:billing_snapshot}") String collectionName
    ) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.collectionName = collectionName;
    }

    @Override
    public void save(Billing billing) {
        if (billing == null) return;

        // ✅ snapshotId = BillingId (workId 기반 결정적)
        String snapshotId = billing.billingId().value();

        Instant now = Instant.now();

        // ✅ workId는 idempotencyKey에 workId를 넣는다고 했으니 거기서 빼는 걸 추천
        // (만약 Billing에 workId 필드가 따로 있으면 그걸 쓰면 됨)
        String workId = billing.idempotencyKey().value();

        // ✅ Billing 전체를 payload 도큐먼트로 변환 (필드 단위 조회 가능)
        Document payloadDoc = toDocumentPayload(billing);

        // ✅ 플랫폼별 조회 최적화용 top-level 필드
        String billingMonth = billing.period().value().atDay(1).toString(); // "YYYY-MM-01"
        Long userId = extractUserId(payloadDoc, billing);

        RecipientTopLevel recipientTop = extractRecipientTopLevel(payloadDoc, billing);
        List<String> availableChannels = computeAvailableChannels(recipientTop);

        Query q = new Query(Criteria.where("_id").is(snapshotId));

        Update u = new Update()
                .setOnInsert("_id", snapshotId)
                .setOnInsert("createdAt", now)
                .set("updatedAt", now)

                .set("workId", workId)
                .set("billingMonth", billingMonth)
                .set("userId", userId)

                .set("availableChannels", availableChannels)
                .set("recipientEmailEnc", recipientTop.emailEnc)
                .set("recipientPhoneEnc", recipientTop.phoneEnc)
                .set("kakaoUserKeyEnc", recipientTop.kakaoUserKeyEnc)

                // 상태/발행시각은 Billing 안에 있다면 같이 top-level로 뽑아두는 게 운영상 좋음
                .set("status", safeString(payloadDoc.get("status")))
                .set("issuedAt", payloadDoc.get("issuedAt")) // 문자열일 수도 있음. 필요하면 별도 매핑 권장

                // ✅ 핵심: Billing 전체 중첩 도큐먼트 저장
                .set("payload", payloadDoc)

                .set("schemaVersion", 1);

        try {
            log.info("upsert");
            mongoTemplate.upsert(q, u, collectionName);
        } catch (Exception e) {
            // snapshot 저장 실패 = 인프라 실패로 보고 step fail로 올리는 게 맞음
            throw new IllegalStateException("Failed to upsert billing snapshot to Mongo", e);
        }
    }

    private Document toDocumentPayload(Billing billing) {
        // Jackson으로 Map 변환 -> Document
        Map<String, Object> map = objectMapper.convertValue(billing, new TypeReference<Map<String, Object>>() {});
        return new Document(map);
    }

    private String extractBillingMonth(Document payload, Billing billing) {
        // payload에 period.billingMonth 같은 게 있으면 그 경로에서 꺼내는 게 좋음.
        // 지금은 가장 단순 fallback: idempotencyKey(workId)=YYYY-MM:userId 라고 가정
        String workId = billing.idempotencyKey().value();
        int idx = workId.indexOf(':');
        if (idx > 0) return workId.substring(0, idx);
        return safeString(payload.get("billingMonth")); // 혹시 Billing에 billingMonth가 직접 있다면
    }

    private Long extractUserId(Document payload, Billing billing) {
        // 1) payload에 userId/customerId가 있으면 거기서
        Object v = payload.get("userId");
        if (v instanceof Number n) return n.longValue();
        v = payload.get("customerId");
        if (v instanceof Map<?,?> m) {
            Object vv = m.get("value");
            if (vv instanceof Number n) return n.longValue();
        }
        // 2) fallback: workId에서 userId 파싱
        String workId = billing.idempotencyKey().value();
        int idx = workId.indexOf(':');
        if (idx > 0) {
            try { return Long.parseLong(workId.substring(idx + 1)); } catch (Exception ignore) {}
        }
        return null;
    }

    private RecipientTopLevel extractRecipientTopLevel(Document payload, Billing billing) {
        // payload.recipient 내부 구조가 record/VO에 따라 다를 수 있음
        // 1) payload에 recipient가 Map으로 들어가면 거기서 추출
        Object r = payload.get("recipient");
        if (r instanceof Map<?,?> rm) {
            String emailEnc = digString(rm, "emailEnc", "email", "value");
            String phoneEnc = digString(rm, "phoneEnc", "phone", "value");
            String kakaoKey = digString(rm, "kakaoUserKeyEnc", "kakaoUserKey", "value");
            return new RecipientTopLevel(emailEnc, phoneEnc, kakaoKey);
        }

        // 2) 없으면 null (운영상 recipient 필드는 payload에 들어가게 하는 걸 권장)
        return new RecipientTopLevel(null, null, null);
    }

    private List<String> computeAvailableChannels(RecipientTopLevel r) {
        List<String> channels = new ArrayList<>();
        if (r.emailEnc != null && !r.emailEnc.isBlank()) channels.add("EMAIL");
        if (r.phoneEnc != null && !r.phoneEnc.isBlank()) channels.add("SMS");
        if (r.kakaoUserKeyEnc != null && !r.kakaoUserKeyEnc.isBlank()) channels.add("KAKAO");
        return channels;
    }

    private static String safeString(Object v) {
        return (v == null) ? null : String.valueOf(v);
    }

    /**
     * recipient 구조가
     * { email: {value:"..."}, phone:{value:"..."} } 처럼 깊을 수도 있어서
     * 후보 키들을 순서대로 파고드는 유틸.
     */
    private static String digString(Map<?, ?> root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(k);
            if (cur == null) return null;
        }
        return (cur == null) ? null : String.valueOf(cur);
    }

    private record RecipientTopLevel(String emailEnc, String phoneEnc, String kakaoUserKeyEnc) {}
}

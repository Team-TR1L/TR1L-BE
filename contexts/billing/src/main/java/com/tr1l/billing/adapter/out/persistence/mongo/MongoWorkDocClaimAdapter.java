package com.tr1l.billing.adapter.out.persistence.mongo;

import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class MongoWorkDocClaimAdapter implements WorkDocClaimPort {

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoWorkDocClaimAdapter(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.step2.work-collection:billing_work}") String collectionName
    ) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    @Override
    public List<ClaimedWorkDoc> claim(YearMonth billingMonth,
                                      int limit,
                                      Duration leaseDuration,
                                      String workerId,
                                      Instant now) {

        // Mongo에는 "YYYY-MM-01" String 저장 컨벤션
        String bm = billingMonth.atDay(1).toString();

        Instant leaseUntil = now.plus(leaseDuration);
        List<ClaimedWorkDoc> claimed = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            Query q = new Query();

            Criteria bmCrit = Criteria.where("billingMonth").is(bm);

            Criteria target = Criteria.where("status").is("TARGET");
            Criteria expired = new Criteria().andOperator(
                    Criteria.where("status").is("PROCESSING"),
                    Criteria.where("leaseUntil").lt(now) // now(Instant) 비교 - 저장 타입이 Date라도 보통 매핑됨
            );

            q.addCriteria(new Criteria().andOperator(bmCrit, new Criteria().orOperator(target, expired)));
            q.with(Sort.by(Sort.Direction.ASC, "userId"));

            Update u = new Update()
                    .set("status", "PROCESSING")
                    .set("workerId", workerId)
                    .set("leaseUntil", leaseUntil)
                    .set("updatedAt", now)
                    .inc("attemptCount", 1);

            FindAndModifyOptions opt = FindAndModifyOptions.options().returnNew(true);

            org.bson.Document doc = mongoTemplate.findAndModify(q, u, opt, org.bson.Document.class, collectionName);
            if (doc == null) break;

            // ---- 안전한 필드 추출(타입 방어) ----

            // (1) _id: ObjectId 대응
            Object rawId = doc.get("_id");
            String id = (rawId == null) ? null : rawId.toString();

            // (2) userId: Number로 받아 long 변환 (int/long 모두 대응)
            Object userIdRaw = doc.get("userId");
            if (!(userIdRaw instanceof Number)) {
                throw new IllegalStateException("Mongo field 'userId' is not a number. value=" + userIdRaw);
            }
            long userId = ((Number) userIdRaw).longValue();

            // (3) attemptCount: Integer 기본값
            int attemptCount = doc.getInteger("attemptCount", 0);

            // (4) billingMonth: String/Date 모두 대응 후 "YYYY-MM"로 정규화
            Object bmRaw = doc.get("billingMonth");
            if (bmRaw == null) {
                throw new IllegalStateException("Mongo field 'billingMonth' is null. docId=" + id);
            }
            String billingMonthStored = bmRaw.toString();      // "YYYY-MM-01" 이거나 Date.toString()일 수도
            String billingMonthStr = billingMonthStored.substring(0, 7); // 내부 컨벤션 "YYYY-MM"

            // (5) leaseUntil: Date/Instant/String 모두 방어
            Object leaseRaw = doc.get("leaseUntil");
            Instant lease;
            if (leaseRaw instanceof Date d) {
                lease = d.toInstant();
            } else if (leaseRaw instanceof Instant inst) {
                lease = inst;
            } else if (leaseRaw != null) {
                // 최후 방어 (예: ISO-8601 문자열로 저장된 경우)
                lease = Instant.parse(leaseRaw.toString());
            } else {
                throw new IllegalStateException("Mongo field 'leaseUntil' is null. docId=" + id);
            }

            claimed.add(new ClaimedWorkDoc(id, billingMonthStr, userId, attemptCount, lease));
        }

        return claimed;
    }
}

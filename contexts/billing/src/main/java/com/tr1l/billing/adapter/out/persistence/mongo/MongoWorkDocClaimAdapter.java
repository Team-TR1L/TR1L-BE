package com.tr1l.billing.adapter.out.persistence.mongo;

import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import org.bson.Document;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * status가 target or Processing&lease를 초과한 유저를 선점
 * findAndModify 원자적으로 선점하여 병렬실행에도 중복 처리 x
 *
 * 게선 : find(limit n) + bulkWrite(update N개) + claimToken으로 성공건만 재조회
 */
@Slf4j
@Component
public class MongoWorkDocClaimAdapter implements WorkDocClaimPort {

    private static final String F_ID = "_id";
    private static final String F_BILLING_MONTH = "billingMonth";
    private static final String F_STATUS = "status"; // target, processing, calculated, (failed)
    private static final String F_USER_ID = "userId";
    private static final String F_WORKER_ID = "workerId";
    private static final String F_LEASE_UNTIL = "leaseUntil";
    private static final String F_UPDATED_AT = "updatedAt";
    private static final String F_ATTEMPT_COUNT = "attemptCount";

    private static final String F_CLAIM_TOKEN = "claimToken";
    private static final String F_CLAIMED_AT = "claimedAt";

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoWorkDocClaimAdapter(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.step2.work-collection:billing_work}") String collectionName

    ) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
        log.info("### MongoWorkDocClaimAdapter init. collection={}", collectionName);

    }

    @Override
    public List<ClaimedWorkDoc> claim(YearMonth billingMonth,
                                      int limit,
                                      Duration leaseDuration,
                                      String workerId,
                                      Instant now) {

        if (limit <= 0) return List.of();

        // Mongo -> YYYY-MM-01 -> "2026-01"
        String bm = billingMonth.atDay(1).toString();
        log.info("### claim called. billingMonth={}, limit={}", billingMonth, limit);

        Instant leaseUntil = now.plus(leaseDuration);

       // 1. processing 처리할 유저 N 조회 Target or Processing&& leaseUntil < now
        Query findQ = new Query();
        findQ.addCriteria(buildClaimableCriteria(bm, now));
        findQ.with(Sort.by(Sort.Direction.ASC, F_USER_ID));
        findQ.limit(limit);

        findQ.fields()
                .include(F_ID)
                .include(F_USER_ID)
                .include(F_BILLING_MONTH)
                .include(F_STATUS)
                .include(F_LEASE_UNTIL)
                .include(F_ATTEMPT_COUNT);

        List<Document> candidates = mongoTemplate.find(findQ, Document.class, collectionName);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. bulkWriter로 N개의 유저를 한번에 bulkupdate -> 문서 반환을 해주지 않음
        String claimToken = UUID.randomUUID().toString();
        BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName);

        for (Document candidate : candidates) {
           Object rawId = candidate.get(F_ID);
           if (rawId == null) continue;;

            Query query = new Query();
            query.addCriteria(new Criteria().andOperator(
                    Criteria.where(F_ID).is(rawId),
                    buildClaimableCriteria(bm, now)
            ));

            Update update = new Update()
                    .set(F_STATUS, "PROCESSING")
                    .set(F_WORKER_ID, workerId)
                    .set(F_LEASE_UNTIL, leaseUntil)
                    .set(F_UPDATED_AT, now)
                    .inc(F_ATTEMPT_COUNT, 1)
                    .set(F_CLAIM_TOKEN, claimToken)
                    .set(F_CLAIMED_AT, now);

            ops.updateOne(query, update);
        }

        // 3. 벌크 연산은 해당 문서를 반환하지 못함 -> claimToken으로 재조회
        var bulkWriteResult = ops.execute();
        int modifiedCount = bulkWriteResult.getModifiedCount();


        // Token을 통해 실제 선점을 성공한 문서를 재조회 후 반환
        Query claimedQ = new Query();
        claimedQ.addCriteria(new Criteria().andOperator(
                Criteria.where(F_CLAIM_TOKEN).is(claimToken),
                Criteria.where(F_WORKER_ID).is(workerId),
                Criteria.where(F_BILLING_MONTH).is(bm),
                Criteria.where(F_STATUS).is("PROCESSING")
        ));
        claimedQ.with(Sort.by(Sort.Direction.ASC, F_USER_ID));

        List<Document> claimedDocs = mongoTemplate.find(claimedQ, Document.class, collectionName);
        List<ClaimedWorkDoc> claimed = claimedDocs.stream()
                .map(this::toClaimedWorkDoc)
                .collect(Collectors.toList());
        return claimed;
    }

    /**
     * 유저 선점 조건 리스트
     *  billingMonth == bm
     *  status == TARGET or stauts == PROCESSING AND leaseUntil < now
     */
    private Criteria buildClaimableCriteria(String bm, Instant now) {
        Criteria bmCrit = Criteria.where(F_BILLING_MONTH).is(bm);

        Criteria target = Criteria.where(F_STATUS).is("TARGET");
        Criteria expired = new Criteria().andOperator(
                Criteria.where(F_STATUS).is("PROCESSING"),
                Criteria.where(F_LEASE_UNTIL).lt(now)
        );

        return new Criteria().andOperator(bmCrit, new Criteria().orOperator(target, expired));
    }

    /**
     * Document -> ClaimedWorkDoc 타입변환
     */
    private ClaimedWorkDoc toClaimedWorkDoc(Document doc) {
        Object rawId = doc.get(F_ID);
        String id = (rawId == null) ? null : rawId.toString();

        Object userIdRaw = doc.get(F_USER_ID);
        if (!(userIdRaw instanceof Number)) {
            throw new IllegalStateException("Mongo field 'userId' is not a number. value=" + userIdRaw + ", docId=" + id);
        }
        long userId = ((Number) userIdRaw).longValue();

        int attemptCount = doc.getInteger(F_ATTEMPT_COUNT, 0);

        Object bmRaw = doc.get(F_BILLING_MONTH);
        if (bmRaw == null) {
            throw new IllegalStateException("Mongo field 'billingMonth' is null. docId=" + id);
        }
        String billingMonthStored = bmRaw.toString();         // "YYYY-MM-01" 예상
        String billingMonthStr = billingMonthStored.length() >= 7
                ? billingMonthStored.substring(0, 7)
                : billingMonthStored;

        Object leaseRaw = doc.get(F_LEASE_UNTIL);
        Instant lease;
        if (leaseRaw instanceof Date d) {
            lease = d.toInstant();
        } else if (leaseRaw instanceof Instant inst) {
            lease = inst;
        } else if (leaseRaw != null) {
            lease = Instant.parse(leaseRaw.toString());
        } else {
            throw new IllegalStateException("Mongo field 'leaseUntil' is null. docId=" + id);
        }

        return new ClaimedWorkDoc(id, billingMonthStr, userId, attemptCount, lease);
    }

}



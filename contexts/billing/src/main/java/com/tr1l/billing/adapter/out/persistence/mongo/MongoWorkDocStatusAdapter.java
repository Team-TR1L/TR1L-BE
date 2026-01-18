package com.tr1l.billing.adapter.out.persistence.mongo;

import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MongoWorkDocStatusAdapter implements WorkDocStatusPort {

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoWorkDocStatusAdapter(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.step2.work-collection:billing_work}") String collectionName
    ) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    @Override
    public void markCalculated(String workId, String snapshotId, Instant now) {
        Query q = new Query(Criteria.where("_id").is(workId));
        Update u = new Update()
                .set("status", "CALCULATED")
                .set("snapshotId", snapshotId)
                .unset("leaseUntil")
                .set("updatedAt", now);

        mongoTemplate.updateFirst(q, u, collectionName);
    }

    @Override
    public void markFailed(String workId, String errorCode, String errorMessage, Instant now) {
        Query q = new Query(Criteria.where("_id").is(workId));
        Update u = new Update()
                .set("status", "FAILED")
                .set("lastErrorCode", errorCode)
                .set("lastErrorMessage", errorMessage)
                .unset("leaseUntil")
                .set("updatedAt", now);

        mongoTemplate.updateFirst(q, u, collectionName);
    }
}
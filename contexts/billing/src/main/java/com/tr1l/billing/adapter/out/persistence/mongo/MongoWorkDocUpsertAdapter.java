package com.tr1l.billing.adapter.out.persistence.mongo;

import com.tr1l.billing.application.port.out.WorkDocUpsertCommand;
import com.tr1l.billing.application.port.out.WorkDocUpsertPort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

/**
 Job1 step2 결과물을 몽고에 저장 하기 위한 어댑터
  */

public class MongoWorkDocUpsertAdapter implements WorkDocUpsertPort {
    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoWorkDocUpsertAdapter(MongoTemplate mongoTemplate, String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    @Override
    public void bulkUpsertOnInsert(List<WorkDocUpsertCommand> commands, Instant now) {
        if (commands == null || commands.isEmpty()) return;

        // 벌크데이터로 쌓아놨다가 한번에 실행
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName);

        for (WorkDocUpsertCommand cmd : commands) {
            Query query = new Query(Criteria.where("_id").is(cmd.id()));

            Update update = new Update()
                    .setOnInsert("_id", cmd.id())
                    .setOnInsert("billingMonth", cmd.billingMonth())
                    .setOnInsert("userId", cmd.userId())
                    .setOnInsert("status", cmd.status())
                    .setOnInsert("attemptCount", cmd.attemptCount())
                    .setOnInsert("createdAt", cmd.createdAt())
                    .set("updatedAt", now);

            bulk.upsert(query, update);
        }

        bulk.execute();
    }
}

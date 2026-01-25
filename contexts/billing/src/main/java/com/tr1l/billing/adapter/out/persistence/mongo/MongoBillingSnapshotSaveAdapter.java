package com.tr1l.billing.adapter.out.persistence.mongo;

import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.vo.Recipient;
import com.tr1l.billing.domain.model.vo.EncryptedEmail;
import com.tr1l.billing.domain.model.vo.EncryptedPhone;
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

@Component
@Slf4j
public class MongoBillingSnapshotSaveAdapter implements BillingSnapshotSavePort {

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoBillingSnapshotSaveAdapter(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.snapshot.collection:billing_snapshot}") String collectionName
    ) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    @Override
    public void save(Billing billing) {
        if (billing == null) return;

        upsertOne(billing, Instant.now());
    }

    @Override
    public void saveAll(List<Billing> billings) {
        if (billings == null || billings.isEmpty()) return;

        Instant now = Instant.now();
        var bulk = mongoTemplate.bulkOps(org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED, collectionName);

        for (Billing billing : billings) {
            Query q = new Query(Criteria.where("_id").is(billing.billingId().value()));
            Update u = buildUpdate(billing, now);
            bulk.upsert(q, u);
        }

        try {
            bulk.execute();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bulk upsert billing snapshots to Mongo", e);
        }
    }

    private void upsertOne(Billing billing, Instant now) {
        Query q = new Query(Criteria.where("_id").is(billing.billingId().value()));
        Update u = buildUpdate(billing, now);

        try {
            mongoTemplate.upsert(q, u, collectionName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upsert billing snapshot to Mongo", e);
        }
    }

    private Update buildUpdate(Billing billing, Instant now) {
        String snapshotId = billing.billingId().value();
        String workId = billing.idempotencyKey().value();
        Document payloadDoc = toRenderPayload(billing);

        String billingMonth = billing.period().value().atDay(1).toString(); // "YYYY-MM-01"
        Long userId = billing.customerId().value();

        return new Update()
                .setOnInsert("_id", snapshotId)
                .setOnInsert("createdAt", now)
                .set("updatedAt", now)

                .set("workId", workId)
                .set("billingMonth", billingMonth)
                .set("userId", userId)

                .set("status", billing.status().name())
                .set("issuedAt", billing.issuedAt())

                .set("payload", payloadDoc)
                .set("schemaVersion", 1);
    }


    private Document toRenderPayload(Billing billing) {
        Document p = new Document();

        p.put("period", new Document("value", billing.period().value().toString()));
        p.put("customerName", new Document("value", billing.customerName().value()));
        p.put("customerBirthDate", new Document("value", billing.customerBirthDate().value().toString()));
        p.put("recipient", toRecipientDoc(billing.recipient()));

        p.put("subtotalAmount", new Document("value", billing.subtotalAmount().amount()));
        p.put("discountTotalAmount", new Document("value", billing.discountTotalAmount().amount()));
        p.put("totalAmount", new Document("value", billing.totalAmount().amount()));

        // chargeLines -> name, pricingSnapshot.amount.value
        List<Document> chargeLines = new ArrayList<>();
        for (var cl : billing.chargeLines()) {
            Document line = new Document();
            line.put("name", cl.displayName()); // 도메인에 맞는 getter로
            line.put("pricingSnapshot",
                    new Document("amount",
                            new Document("value", cl.pricingSnapshot().lineAmount().amount())
                    )
            );
            chargeLines.add(line);
        }
        p.put("chargeLines", chargeLines);

        // discountLines -> name, discountType, discountAmount.value
        List<Document> discountLines = new ArrayList<>();
        for (var dl : billing.discountLines()) {
            Document line = new Document();
            line.put("name", dl.displayName());
            line.put("discountType", dl.type().name()); // enum이면 name()
            line.put("discountAmount", new Document("value", dl.discountAmount().amount()));
            discountLines.add(line);
        }
        p.put("discountLines", discountLines);

        return p;
    }

    private Document toRecipientDoc(Recipient recipient) {
        String emailCipher = recipient.email().map(EncryptedEmail::cipherText).orElse(null);
        String phoneCipher = recipient.phone().map(EncryptedPhone::cipherText).orElse(null);

        Document r = new Document();
        r.put("email", new Document("value", emailCipher));
        r.put("phone", new Document("value", phoneCipher));
        return r;
    }
}

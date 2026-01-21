package com.tr1l.billing.adapter.out.persistence;

import com.tr1l.billing.application.port.out.WorkDocFinalizeQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 ==========================
 *
 * step4에서 사용될수 있는 mongoAdapter
 * @author nonstop
 * @version 1.0.0
 * @date $date
 * ========================== */
@RequiredArgsConstructor
public class WorkDocFinalizeMongoAdapter implements WorkDocFinalizeQueryPort {
    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    //
    @Override
    public FinalizeCheckResult countForFinalize(String billingMonth) {

        long pendingCount = mongoTemplate.count(
                Query.query(
                        Criteria.where("billingMonth").is(billingMonth)
                                .and("status").in("TARGET","PROCESSING")
                ),
                collectionName
        );

        long failedCount = mongoTemplate.count(
                Query.query(
                        Criteria.where("billingMonth").is(billingMonth)
                                .and("status").is("FAILED")),
                collectionName
        );

        return new FinalizeCheckResult(pendingCount,failedCount);

    }
}

package com.tr1l.worker.batch.formatjob.step.step1;


import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.data.MongoCursorItemReader;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.YearMonth;

/**
 * ==========================
 * BillingSnapShotReader
 * Job2의 step1
 * billing snapshot 에서 요금서에 필요한 정보들을 읽어서 Processor에 전달
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */


@Slf4j
@StepScope
public class BillingSnapShotReader extends MongoCursorItemReader<BillingSnapshotDoc> {

    public BillingSnapShotReader(
            MongoTemplate mongoTemplate,
            String collectionName,
            String billingMonth
    ) {
        String billingMonthDay = YearMonth.parse(billingMonth).atDay(1).toString();
        Query query = new Query();
        query.addCriteria(Criteria.where("billingMonth").is(billingMonthDay));
        //query.addCriteria(Criteria.where("status").is("ISSUED"));


        query.fields()
                .include("_id")
                .include("billingMonth")
                .include("userId")
                .include("status")
                .include("issuedAt")
                .include("workId")
                .include("payload.recipient.email.value")   // Enc면 recipientEmailEnc로 수정
                .include("payload.recipient.phone.value")
                .include("payload.customerBirthDate.value")
                .include("payload.period.value")
                .include("payload.customerName.value")
                .include("payload.subtotalAmount.value")
                .include("payload.discountTotalAmount.value")
                .include("payload.totalAmount.value")
                .include("payload.chargeLines.name")
                .include("payload.chargeLines.pricingSnapshot.amount.value")
                .include("payload.discountLines.name")
                .include("payload.discountLines.discountType")
                .include("payload.discountLines.discountAmount.value");
        query.with(Sort.by(Sort.Direction.ASC,"_id"));
        log.warn(" ============== query finished ===========");

        this.setName("billingSnapshotReader");   // restart 시 필요
        this.setSaveState(true);                 // restart 시 필요
        this.setTemplate(mongoTemplate);
        this.setCollection(collectionName);
        this.setQuery(query);
        this.setTargetType(BillingSnapshotDoc.class);

        log.info("BillingSnapShotReader query prepared. billingMonthDay={}, status=ISSUED", billingMonthDay);

        log.warn("query = {}", query);

    }
}

package com.tr1l.worker.batch.formatjob.step.step1;


import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.YearMonth;
import java.util.Iterator;
import java.util.List;

/**
 * ==========================
 * BillingSnapShotReader
 * billing snapshot 에서 요금서에 필요한 정보들을 읽어서 Processor에 전달
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */


@Slf4j
@StepScope
public class BillingSnapshotKeysetReader extends ItemStreamSupport implements ItemStreamReader<BillingSnapshotDoc> {


    /**
     * ExecutionContext에 저장될 체크포인트 key
     * - Step 재시작 시 이 key로 lastUserId를 복구한다.
     */
    private static final String EC_LAST_USER_ID = "billingSnapshot.lastUserId";


    private final MongoTemplate mongoTemplate;
    private final String collectionName;
    private final String billingMonth;
    private final int pageSize;
    private long lastUserId;
    private Iterator<BillingSnapshotDoc> iterator;
    private boolean noMoreData = false;

    public BillingSnapshotKeysetReader(
            MongoTemplate mongoTemplate,
            String collectionName,
            String billingMonth,   // 예: "2026-01"
            int pageSize
    ) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
        this.billingMonth = YearMonth.parse(billingMonth).atDay(1).toString();
        this.pageSize = pageSize;

        // Step 재시작 시 Reader state 저장/복구에 사용되는 name
        // (ItemStreamSupport가 관리)
        setName("billingSnapshotKeysetReader");
    }

    @Override
    public BillingSnapshotDoc read() {
        //이미 데이터가 끝났으면 즉시 종료
        if (noMoreData) {
            return null;
        }

        if (iterator == null || !iterator.hasNext()) {
            List<BillingSnapshotDoc> page = fetchNextPage();

            // 조회 결과가 0이면 더 이상 데이터 없음 -> Step 종료
            if (page.isEmpty()) {
                noMoreData = true;
                return null;
            }
            iterator = page.iterator();
        }
        BillingSnapshotDoc doc = iterator.next();
        this.lastUserId = doc.userId();
        return doc;
    }

    private List<BillingSnapshotDoc> fetchNextPage() {
        Query q = new Query();

        // 고정 필터: 이번 배치 대상 month
        q.addCriteria(Criteria.where("billingMonth").is(billingMonth));

        // Keyset 조건: 마지막으로 처리한 userId 이후만 읽기
        q.addCriteria(Criteria.where("userId").gt(lastUserId));

        // 정렬: userId 오름차순 (Keyset은 정렬 기준과 where 조건이 같아야 함)
        q.with(Sort.by(Sort.Direction.ASC, "userId"));

        // 페이지 사이즈만큼만 가져오기
        q.limit(pageSize);

        // 컬럼 가져오기
        q.fields()
                .include("billingMonth")
                .include("userId")
                .include("status")
                .include("issuedAt")
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

        // 실제 조회
        List<BillingSnapshotDoc> result =
                mongoTemplate.find(q, BillingSnapshotDoc.class, collectionName);

        if (!result.isEmpty()) {
            long first = result.get(0).userId();
            long last = result.get(result.size() - 1).userId();
            log.info("Keyset page fetched. billingMonthDay={}, lastUserId(before)={}, fetched={}, range=[{}..{}]",
                    billingMonth, lastUserId, result.size(), first, last);
        } else {
            log.info("Keyset page fetched EMPTY. billingMonthDay={}, lastUserId(before)={}",
                    billingMonth, lastUserId);
        }

        return result;
    }

    /**
     * Step 시작 시 호출
     * <p>
     * - 재시작이면 ExecutionContext에 저장된 lastUserId를 꺼내서 이어서 읽는다.
     * - 처음 실행이면 lastUserId=0(또는 -1)부터 시작한다.
     */
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        super.open(executionContext);

        // ExecutionContext 복구: 없으면 처음 실행
        this.lastUserId = executionContext.containsKey(EC_LAST_USER_ID)
                ? executionContext.getLong(EC_LAST_USER_ID)
                : 0L;

        // 내부 상태 초기화
        this.iterator = null;
        this.noMoreData = false;

        log.info("BillingSnapshotKeysetReader opened. billingMonth={}, startLastUserId={}, pageSize={}",
                billingMonth, lastUserId, pageSize);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        super.update(executionContext);

        // 체크포인트 저장
        executionContext.putLong(EC_LAST_USER_ID, this.lastUserId);
    }


    /**
     * Step 종료 시 호출
     * - iterator 등 내부 상태 정리
     */
    @Override
    public void close() throws ItemStreamException {
        super.close();
        this.iterator = null;
        this.noMoreData = false;
    }


}

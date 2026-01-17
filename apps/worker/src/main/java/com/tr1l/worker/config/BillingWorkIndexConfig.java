package com.tr1l.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Mongo billing_work 컬렉션 인덱스 초기화
 * - 앱 시작 시 컬렉션이 없으면 생성하고, 인덱스가 없으면 자동 생성(ensure)
 * - MongoTemplate이 존재할 때만 실행되므로, Mongo 미사용 모드(pg 등)에서는 자동 패스
 *  [복합 인덱스] billingMonth + status + userId
 *  목적: Work 컬렉션을 '작업 큐'처럼 조회할 때 가장 자주 쓰는 조건을 한 방에 커버한다.
 *
 *  1) 이번 달 대상만 빠르게 조회
 *     - 예) find({ billingMonth: "2026-01", status: "TARGET" })
 *     - billingMonth가 선두라서 "이번 달" 범위를 먼저 좁힌 뒤 status로 더 좁힌다.
 *
 *  2) 상태별 집계/재시도 대상 조회 최적화
 *     - 예) find({ billingMonth: "2026-01", status: "FAILED" })
 *     - 예) count({ billingMonth: "2026-01", status: "CALCULATED" })
 *
 *  3) userId 정렬(sort) 비용 절감(또는 범위 스캔에 도움)
 *     - 예) find({ billingMonth: "2026-01", status: "TARGET" }).sort({ userId: 1 })
 *     - 인덱스에 userId가 포함되어 있어 정렬이 인덱스 순서를 그대로 이용할 수 있다.
 *
 *  왜 순서가 billingMonth → status → userId 인가?
 *  - Mongo 인덱스는 "앞(prefix)부터" 조건을 태워야 가장 효율적이다.
 *  - 우리 쿼리는 대부분 billingMonth(이번달) + status(대상/처리중/실패...)로 필터링하므로
 *    이 두 개를 앞에 두는 게 가장 이득.
 *  - userId는 정렬/안정적 처리 순서(혹은 워커 분산) 때문에 3번째에 둔다.
 */
@Configuration
@ConditionalOnBean(MongoTemplate.class)
public class BillingWorkIndexConfig {

    @Bean
    public ApplicationRunner billingWorkIndexes(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.step2.work-collection:billing_work}") String collectionName
    ) {

        return args -> {
            if (!mongoTemplate.collectionExists(collectionName)) {
                mongoTemplate.createCollection(collectionName);
            }

            mongoTemplate.indexOps(collectionName).createIndex(
                    new Index()
                            .on("billingMonth", Sort.Direction.ASC)
                            .on("status", Sort.Direction.ASC)
                            .on("userId", Sort.Direction.ASC)
                            .named("idx_bm_status_user")
            );
        };
    }
}

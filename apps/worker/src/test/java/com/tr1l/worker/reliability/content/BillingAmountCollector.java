package com.tr1l.worker.reliability.content;

import com.mongodb.client.MongoClient;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.bson.Document;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 참조 계산 금액과 snapshot 금액 수집기
public final class BillingAmountCollector {
    private static final String REFERENCE_SQL = "reliability/checks/reference/job1-billing-amount.sql";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MongoClient mongoClient;
    private final String mongoDatabase;
    private final String snapshotCollection;
    private final ReliabilityResourceCatalog catalog;

    public BillingAmountCollector(
            NamedParameterJdbcTemplate jdbcTemplate,
            MongoClient mongoClient,
            String mongoDatabase,
            String snapshotCollection,
            ReliabilityResourceCatalog catalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoDatabase;
        this.snapshotCollection = snapshotCollection;
        this.catalog = catalog;
    }

    // billing_targets 기준 참조 계산
    public Map<Long, BillingAmounts> expected(LocalDate billingMonthDay) {
        Map<Long, BillingAmounts> amounts = new TreeMap<>();
        jdbcTemplate.query(
                catalog.loadCheckText(REFERENCE_SQL),
                new MapSqlParameterSource("billingMonthDay", billingMonthDay),
                rs -> {
                    amounts.put(rs.getLong("user_id"), new BillingAmounts(
                            rs.getLong("subtotal"),
                            rs.getLong("discount_total"),
                            rs.getLong("total"),
                            rs.getLong("contract_discount"),
                            rs.getLong("soldier_discount"),
                            rs.getLong("welfare_discount")
                    ));
                });
        return amounts;
    }

    // Mongo snapshot 금액
    public Map<Long, BillingAmounts> actual(LocalDate billingMonthDay) {
        Map<Long, BillingAmounts> amounts = new TreeMap<>();
        Document filter = new Document("billingMonth", billingMonthDay.toString());
        for (Document snapshot : mongoClient.getDatabase(mongoDatabase).getCollection(snapshotCollection).find(filter)) {
            Document payload = snapshot.get("payload", Document.class);
            amounts.put(((Number) snapshot.get("userId")).longValue(), new BillingAmounts(
                    valueOf(payload.get("subtotalAmount", Document.class)),
                    valueOf(payload.get("discountTotalAmount", Document.class)),
                    valueOf(payload.get("totalAmount", Document.class)),
                    discountOf(payload, "CONTRACT_25P"),
                    discountOf(payload, "SOLDIER"),
                    discountOf(payload, "WELFARE")
            ));
        }
        return amounts;
    }

    // 할인 종류별 합
    private long discountOf(Document payload, String discountType) {
        List<Document> lines = payload.getList("discountLines", Document.class, List.of());
        return lines.stream()
                .filter(line -> discountType.equals(line.getString("discountType")))
                .mapToLong(line -> valueOf(line.get("discountAmount", Document.class)))
                .sum();
    }

    private long valueOf(Document money) {
        return money == null ? 0 : ((Number) money.get("value")).longValue();
    }
}

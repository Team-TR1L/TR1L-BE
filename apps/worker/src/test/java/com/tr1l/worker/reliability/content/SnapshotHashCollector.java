package com.tr1l.worker.reliability.content;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

// 사용자별 snapshot 해시 수집기
// 같은 사용자 snapshot이 둘 이상이면 따로 센다
public final class SnapshotHashCollector {
    private final MongoClient mongoClient;
    private final String mongoDatabase;
    private final String snapshotCollection;
    private final SnapshotCanonicalizer canonicalizer = new SnapshotCanonicalizer();

    public SnapshotHashCollector(MongoClient mongoClient, String mongoDatabase, String snapshotCollection) {
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoDatabase;
        this.snapshotCollection = snapshotCollection;
    }

    public SnapshotHashes collect(LocalDate billingMonthDay) {
        MongoCollection<Document> collection = mongoClient.getDatabase(mongoDatabase).getCollection(snapshotCollection);

        // userId 기준 해시
        Map<Long, String> hashes = new TreeMap<>();
        // 중복 snapshot 사용자
        Map<Long, Integer> duplicates = new TreeMap<>();

        for (Document snapshot : collection.find(new Document("billingMonth", billingMonthDay.toString()))) {
            long userId = ((Number) snapshot.get("userId")).longValue();
            String previous = hashes.put(userId, canonicalizer.sha256(snapshot));
            if (previous != null) {
                duplicates.merge(userId, 2, (count, ignored) -> count + 1);
            }
        }

        return new SnapshotHashes(billingMonthDay.toString(), hashes, duplicates);
    }
}

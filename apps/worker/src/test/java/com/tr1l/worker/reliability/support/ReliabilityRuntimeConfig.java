package com.tr1l.worker.reliability.support;

// 로컬 기본값 묶음
public record ReliabilityRuntimeConfig(
        String targetJdbcUrl,
        String targetJdbcUsername,
        String targetJdbcPassword,
        String mongoUri,
        String mongoDatabase,
        String snapshotCollection
) {
    public static ReliabilityRuntimeConfig fromEnvironment() {
        return new ReliabilityRuntimeConfig(
                env("PG_SUB_HOST", "jdbc:postgresql://localhost:5433/billing_target"),
                env("PG_SUB_USER", "postgres"),
                env("PG_SUB_PASSWORD", "postgres"),
                env("MONGODB_URI", "mongodb://localhost:27017/tr1l"),
                env("JOB1_RELIABILITY_MONGO_DB", "tr1l"),
                env("JOB1_RELIABILITY_SNAPSHOT_COLLECTION", "billing_snapshot")
        );
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

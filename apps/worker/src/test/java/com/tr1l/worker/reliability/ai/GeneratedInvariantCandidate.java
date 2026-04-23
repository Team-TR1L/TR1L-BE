package com.tr1l.worker.reliability.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

// 생성 후보 스키마 정의
public record GeneratedInvariantCandidate(
        @JsonProperty("id")
        String id,
        @JsonProperty("category")
        String category,
        @JsonProperty("description")
        String description,
        @JsonProperty("scope")
        String scope,
        @JsonProperty("sql_check")
        String sqlCheck,
        @JsonProperty("violated_by")
        String violatedBy,
        @JsonProperty("severity")
        String severity
) {
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "state_consistency",
            "count_consistency",
            "temporal",
            "referential"
    );
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            "always",
            "rerun_after_crash",
            "step_complete"
    );
    private static final Set<String> ALLOWED_SEVERITIES = Set.of(
            "critical",
            "major",
            "minor"
    );

    public GeneratedInvariantCandidate {
        id = requireText(id, "id");
        category = requireAllowed(category, "category", ALLOWED_CATEGORIES);
        description = requireText(description, "description");
        scope = requireAllowed(scope, "scope", ALLOWED_SCOPES);
        sqlCheck = requireText(sqlCheck, "sqlCheck");
        violatedBy = requireText(violatedBy, "violatedBy");
        severity = requireAllowed(severity, "severity", ALLOWED_SEVERITIES);
    }

    // cross db 후보 판별
    public boolean crossDb() {
        return sqlCheck.contains("MongoDB") || sqlCheck.contains("cross-db");
    }

    // 필수값 방어
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is blank");
        }
        return trimmed;
    }

    // 허용값 방어
    private static String requireAllowed(String value, String fieldName, Set<String> allowedValues) {
        String trimmed = requireText(value, fieldName);
        if (!allowedValues.contains(trimmed)) {
            throw new IllegalArgumentException(fieldName + " is not allowed: " + trimmed);
        }
        return trimmed;
    }
}

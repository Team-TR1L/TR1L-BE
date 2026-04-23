package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.invariant.InvariantDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// generated invariant 별도 검토자
public final class GeneratedInvariantConsistencyChecker {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final double DUPLICATE_THRESHOLD = 0.25d;

    public InvariantConsistencyReport check(
            List<GeneratedInvariantCandidate> candidates,
            List<InvariantDefinition> activeInvariants
    ) {
        List<InvariantConsistencyReview> reviews = candidates.stream()
                .map(candidate -> checkOne(candidate, activeInvariants))
                .toList();

        int passCount = countByDecision(reviews, InvariantConsistencyDecision.PASS);
        int warnCount = countByDecision(reviews, InvariantConsistencyDecision.WARN);
        int failCount = countByDecision(reviews, InvariantConsistencyDecision.FAIL);

        return new InvariantConsistencyReport(
                reviews,
                reviews.size(),
                passCount,
                warnCount,
                failCount
        );
    }

    // 단건 검토
    private InvariantConsistencyReview checkOne(
            GeneratedInvariantCandidate candidate,
            List<InvariantDefinition> activeInvariants
    ) {
        List<InvariantConsistencyFinding> findings = new ArrayList<>();

        checkSqlShape(candidate, findings);
        checkCategoryMeaning(candidate, findings);
        checkTemporalScope(candidate, findings);
        checkDuplicateActive(candidate, activeInvariants, findings);

        InvariantConsistencyDecision decision = decide(findings);

        return new InvariantConsistencyReview(
                candidate.id(),
                decision,
                findings,
                suggestedAction(decision)
        );
    }

    // SQL 형태 검토
    private void checkSqlShape(GeneratedInvariantCandidate candidate, List<InvariantConsistencyFinding> findings) {
        String sql = candidate.sqlCheck().trim();
        String normalized = sql.toUpperCase(Locale.ROOT);

        if (candidate.crossDb()) {
            findings.add(new InvariantConsistencyFinding(
                    "CROSS_DB_REVIEW_REQUIRED",
                    "WARN",
                    "sql_check",
                    "cross-db 검증은 SQL 단독 실행보다 별도 executor 검토 필요"
            ));
            return;
        }

        if (!normalized.startsWith("SELECT") || !normalized.contains("FROM") || normalized.contains("...")) {
            findings.add(new InvariantConsistencyFinding(
                    "SQL_SHAPE_INVALID",
                    "FAIL",
                    "sql_check",
                    "PostgreSQL 단독 실행 가능한 SELECT 형태가 아님"
            ));
        }
    }

    // category 와 SQL 의미 검토
    private void checkCategoryMeaning(GeneratedInvariantCandidate candidate, List<InvariantConsistencyFinding> findings) {
        String sql = candidate.sqlCheck().toUpperCase(Locale.ROOT);
        String description = normalizeText(candidate.description());

        boolean aligned = switch (candidate.category()) {
            case "count_consistency" -> sql.contains("COUNT(") || description.contains("집합");
            case "state_consistency" -> sql.contains("STATUS") || sql.contains("GROUP BY") || description.contains("상태");
            case "temporal" -> sql.contains("LEASE_UNTIL") || sql.contains("NOW()") || description.contains("재실행");
            case "referential" -> candidate.crossDb()
                    || sql.contains("WORK_ID")
                    || sql.contains("WORKID")
                    || description.contains("snapshot");
            default -> false;
        };

        if (!aligned) {
            findings.add(new InvariantConsistencyFinding(
                    "CATEGORY_SQL_MISMATCH",
                    "FAIL",
                    "category",
                    "category 와 sql_check 또는 description 의 의미가 어긋남"
            ));
        }
    }

    // temporal scope 검토
    private void checkTemporalScope(GeneratedInvariantCandidate candidate, List<InvariantConsistencyFinding> findings) {
        if (!"temporal".equals(candidate.category())) {
            return;
        }

        String description = normalizeText(candidate.description());
        if ("always".equals(candidate.scope()) && description.contains("재실행")) {
            findings.add(new InvariantConsistencyFinding(
                    "TEMPORAL_SCOPE_REVIEW",
                    "WARN",
                    "scope",
                    "재실행 완료 뒤 조건일 수 있어 always 범위 재검토 필요"
            ));
        }
    }

    // active 중복 검토
    private void checkDuplicateActive(
            GeneratedInvariantCandidate candidate,
            List<InvariantDefinition> activeInvariants,
            List<InvariantConsistencyFinding> findings
    ) {
        Set<String> candidateTokens = tokenize(candidate.description());

        activeInvariants.stream()
                .filter(active -> candidate.category().equals(active.category()))
                .map(active -> new ActiveSimilarity(active.id(), jaccard(candidateTokens, tokenize(active.description()))))
                .max(Comparator.comparingDouble(ActiveSimilarity::similarity))
                .filter(match -> match.similarity() >= DUPLICATE_THRESHOLD)
                .ifPresent(match -> findings.add(new InvariantConsistencyFinding(
                        "ACTIVE_DUPLICATE_REVIEW",
                        "WARN",
                        "description",
                        "기존 active invariant " + match.activeInvariantId() + " 와 의미 중복 가능성 존재"
                )));
    }

    // 판정 계산
    private InvariantConsistencyDecision decide(List<InvariantConsistencyFinding> findings) {
        boolean hasFail = findings.stream().anyMatch(finding -> "FAIL".equals(finding.severity()));
        if (hasFail) {
            return InvariantConsistencyDecision.FAIL;
        }

        boolean hasWarn = findings.stream().anyMatch(finding -> "WARN".equals(finding.severity()));
        return hasWarn ? InvariantConsistencyDecision.WARN : InvariantConsistencyDecision.PASS;
    }

    // 후속 조치 문구
    private String suggestedAction(InvariantConsistencyDecision decision) {
        return switch (decision) {
            case PASS -> "active 승격 후보로 검토 가능";
            case WARN -> "사람 검토 뒤 승격 여부 판단 필요";
            case FAIL -> "프롬프트 또는 SQL 수정 뒤 재생성 필요";
        };
    }

    private int countByDecision(List<InvariantConsistencyReview> reviews, InvariantConsistencyDecision decision) {
        return (int) reviews.stream()
                .filter(review -> review.decision() == decision)
                .count();
    }

    // 공백 정리
    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    // 토큰 분리
    private Set<String> tokenize(String text) {
        return TOKEN_SPLIT.splitAsStream(normalizeText(text))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    // 유사도 계산
    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }

        long intersection = left.stream().filter(right::contains).count();
        long union = left.size() + right.size() - intersection;
        return union == 0 ? 0d : (double) intersection / union;
    }

    private record ActiveSimilarity(
            String activeInvariantId,
            double similarity
    ) {
    }
}

package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.invariant.InvariantDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// generated 후보 점수화 전용
public final class GeneratedInvariantScorer {
    private static final int FORMAT_SCORE = 20;
    private static final int SQL_EXECUTABLE_SCORE = 25;
    private static final int SEMANTIC_ALIGNMENT_SCORE = 25;
    private static final int SCENARIO_LINKAGE_SCORE = 15;
    private static final int NOVELTY_SCORE = 15;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final double DUPLICATE_THRESHOLD = 0.25d;

    public GeneratedInvariantBatchEvaluation evaluate(
            List<GeneratedInvariantCandidate> candidates,
            List<InvariantDefinition> activeInvariants
    ) {
        List<GeneratedInvariantEvaluation> evaluations = candidates.stream()
                .map(candidate -> evaluateOne(candidate, activeInvariants))
                .toList();

        int totalCandidates = evaluations.size();
        int activeCandidateCount = (int) evaluations.stream()
                .filter(evaluation -> evaluation.decision() == GeneratedInvariantPromotionDecision.ACTIVE_CANDIDATE)
                .count();
        int reviewRequiredCount = (int) evaluations.stream()
                .filter(evaluation -> evaluation.decision() == GeneratedInvariantPromotionDecision.REVIEW_REQUIRED)
                .count();
        int rejectCount = (int) evaluations.stream()
                .filter(evaluation -> evaluation.decision() == GeneratedInvariantPromotionDecision.REJECT)
                .count();
        double averageScore = evaluations.stream()
                .mapToInt(GeneratedInvariantEvaluation::totalScore)
                .average()
                .orElse(0d);

        return new GeneratedInvariantBatchEvaluation(
                evaluations,
                totalCandidates,
                activeCandidateCount,
                reviewRequiredCount,
                rejectCount,
                averageScore
        );
    }

    // 단건 평가
    private GeneratedInvariantEvaluation evaluateOne(
            GeneratedInvariantCandidate candidate,
            List<InvariantDefinition> activeInvariants
    ) {
        boolean sqlExecutable = isSqlExecutable(candidate);
        boolean semanticallyAligned = isSemanticallyAligned(candidate);
        boolean scenarioLinked = isScenarioLinked(candidate);
        NoveltyMatch noveltyMatch = findNoveltyMatch(candidate, activeInvariants);

        int formatScore = FORMAT_SCORE;
        int sqlExecutableScore = sqlExecutable ? SQL_EXECUTABLE_SCORE : 0;
        int semanticAlignmentScore = semanticallyAligned ? SEMANTIC_ALIGNMENT_SCORE : 0;
        int scenarioLinkageScore = scenarioLinked ? SCENARIO_LINKAGE_SCORE : 0;
        int noveltyScore = noveltyMatch.novel() ? NOVELTY_SCORE : 0;
        int totalScore = formatScore + sqlExecutableScore + semanticAlignmentScore + scenarioLinkageScore + noveltyScore;

        GeneratedInvariantPromotionDecision decision = decide(totalScore, sqlExecutable, noveltyMatch.novel());

        return new GeneratedInvariantEvaluation(
                candidate.id(),
                formatScore,
                sqlExecutableScore,
                semanticAlignmentScore,
                scenarioLinkageScore,
                noveltyScore,
                totalScore,
                sqlExecutable,
                semanticallyAligned,
                scenarioLinked,
                noveltyMatch.novel(),
                noveltyMatch.matchedActiveId(),
                noveltyMatch.similarity(),
                decision
        );
    }

    // 실행 가능 판정
    private boolean isSqlExecutable(GeneratedInvariantCandidate candidate) {
        String sql = candidate.sqlCheck().trim();
        String normalized = sql.toUpperCase(Locale.ROOT);

        if (candidate.crossDb()) {
            return sql.contains("cross-db") || sql.contains("MongoDB");
        }

        return normalized.startsWith("SELECT")
                && normalized.contains("FROM")
                && !normalized.contains("...");
    }

    // 의미 일치 판정
    private boolean isSemanticallyAligned(GeneratedInvariantCandidate candidate) {
        String description = normalizeText(candidate.description());
        String sql = candidate.sqlCheck().toUpperCase(Locale.ROOT);

        return switch (candidate.category()) {
            case "count_consistency" ->
                    (description.contains("수") || description.contains("count"))
                            && sql.contains("COUNT(");
            case "state_consistency" ->
                    (description.contains("최대") || description.contains("중복"))
                            && sql.contains("GROUP BY")
                            && sql.contains("HAVING");
            case "temporal" ->
                    description.contains("재실행")
                            && (sql.contains("LEASE_UNTIL") || sql.contains("NOW()") || sql.contains("PROCESSING"));
            case "referential" ->
                    (description.contains("mongodb") || sql.contains("MONGODB") || candidate.crossDb())
                            && (description.contains("workid") || sql.contains("WORK_ID") || sql.contains("WORKID"));
            default -> false;
        };
    }

    // 시나리오 연결 판정
    private boolean isScenarioLinked(GeneratedInvariantCandidate candidate) {
        String violatedBy = normalizeText(candidate.violatedBy());
        if (violatedBy.isBlank()) {
            return false;
        }

        return violatedBy.contains("중복")
                || violatedBy.contains("재실행")
                || violatedBy.contains("processing")
                || violatedBy.contains("snapshot")
                || violatedBy.contains("mongo")
                || violatedBy.contains("lease")
                || violatedBy.contains("partial")
                || violatedBy.contains("수렴");
    }

    // 신규성 판정
    private NoveltyMatch findNoveltyMatch(
            GeneratedInvariantCandidate candidate,
            List<InvariantDefinition> activeInvariants
    ) {
        Set<String> candidateTokens = tokenize(candidate.description());

        return activeInvariants.stream()
                .filter(active -> candidate.category().equals(active.category()))
                .map(active -> new NoveltyMatch(
                        active.id(),
                        jaccard(candidateTokens, tokenize(active.description()))
                ))
                .max(Comparator.comparingDouble(NoveltyMatch::similarity))
                .map(match -> match.similarity() >= DUPLICATE_THRESHOLD
                        ? new NoveltyMatch(false, match.matchedActiveId(), match.similarity())
                        : new NoveltyMatch(true, null, match.similarity()))
                .orElseGet(() -> new NoveltyMatch(true, null, 0d));
    }

    // 최종 판정
    private GeneratedInvariantPromotionDecision decide(int totalScore, boolean sqlExecutable, boolean novel) {
        if (!sqlExecutable || totalScore < 60) {
            return GeneratedInvariantPromotionDecision.REJECT;
        }
        if (totalScore >= 85 && novel) {
            return GeneratedInvariantPromotionDecision.ACTIVE_CANDIDATE;
        }
        return GeneratedInvariantPromotionDecision.REVIEW_REQUIRED;
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

    // 신규성 비교 결과
    private record NoveltyMatch(
            boolean novel,
            String matchedActiveId,
            double similarity
    ) {
        private NoveltyMatch(String matchedActiveId, double similarity) {
            this(false, matchedActiveId, similarity);
        }
    }
}

package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;

import java.util.ArrayList;
import java.util.List;

// generated 후보 active 승격 gate
public final class GeneratedInvariantReviewGate {
    private static final int ACTIVE_PROMOTION_SCORE = 85;

    public ReviewGateResult evaluate(
            HumanReviewRecord review,
            InvariantDefinition promoted,
            ReliabilityResourceCatalog catalog
    ) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        HumanReviewScoreSummary score = review.scoreSummary();
        HumanReviewPromotionTarget target = review.promotionTarget();

        if (!"approved".equalsIgnoreCase(review.reviewStatus())) {
            blockers.add("reviewStatus 가 approved 가 아님");
        }
        if (score.totalScore() < ACTIVE_PROMOTION_SCORE) {
            blockers.add("totalScore 가 active 승격 기준보다 낮음");
        }
        if (!score.sqlExecutable()) {
            blockers.add("SQL 실행 가능 점수가 false");
        }
        if (!score.semanticallyAligned()) {
            blockers.add("자연어 설명과 SQL 의미 일치 검토 실패");
        }
        if (!score.novelAgainstActive()) {
            blockers.add("기존 active invariant 와 중복 가능성 존재");
        }
        if (!score.scenarioLinked() && review.reviewNotes().isEmpty()) {
            blockers.add("scenarioLinked false 인데 보완 검토 메모가 없음");
        }
        if (!score.scenarioLinked() && !review.reviewNotes().isEmpty()) {
            warnings.add("scenarioLinked false 를 사람 검토 메모로 보완");
        }
        if (!target.activeInvariantId().equals(promoted.id())) {
            blockers.add("promotionTarget activeInvariantId 와 active 파일 id 불일치");
        }
        if (!target.checkRef().equals(promoted.checkRef())) {
            blockers.add("promotionTarget checkRef 와 active checkRef 불일치");
        }
        if (!catalog.resourceExists(target.activeInvariantFile())) {
            blockers.add("activeInvariantFile 리소스 누락");
        }
        if (!catalog.resourceExists(target.checkRef())) {
            blockers.add("checkRef 리소스 누락");
        }

        return new ReviewGateResult(
                review.generatedInvariantId(),
                target.activeInvariantId(),
                blockers.isEmpty(),
                blockers,
                warnings
        );
    }
}

package com.tr1l.worker.reliability;

import com.fasterxml.jackson.databind.JsonNode;
import com.tr1l.worker.reliability.dataset.DatasetDistributionCheck;
import com.tr1l.worker.reliability.dataset.DatasetDistributionResult;
import com.tr1l.worker.reliability.dataset.DimensionProfile;
import com.tr1l.worker.reliability.dataset.InputDimensions;
import com.tr1l.worker.reliability.dataset.Job1DatasetDefinition;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// 데이터셋 생성기와 분포 검증 규칙 고정
// DB 없이 리소스와 계산만 본다
class Job1DatasetGeneratorContractTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();

    @Test
    @DisplayName("pairwise 커버리지가 나온 값 쌍 수를 정확히 세는지 보기")
    void pairwiseCoverageShouldCountObservedPairs() {
        Map<String, InputDimensions.Dimension> dimensions = new LinkedHashMap<>();
        dimensions.put("a", new InputDimensions.Dimension("a", List.of("x", "y")));
        dimensions.put("b", new InputDimensions.Dimension("b", List.of("p", "q")));
        InputDimensions definition = new InputDimensions(dimensions);

        DimensionProfile profile = DimensionProfile.of(definition, List.of(
                Map.of("a", "x", "b", "p"),
                Map.of("a", "x", "b", "q"),
                Map.of("a", "y", "b", "p"),
                Map.of("a", "z", "b", "p")
        ));

        assertThat(profile.possiblePairs()).isEqualTo(4);
        // 마지막 사용자는 정의에 없는 값이라 쌍으로 세지 않는다
        assertThat(profile.coveredPairs()).isEqualTo(3);
        assertThat(profile.missingPairs()).containsExactly("a=y × b=q");
        assertThat(profile.unknownValues()).containsExactly("a=z");
        assertThat(profile.ratio("a", "x")).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("비율 항목이 허용 오차 밖이면 실패로 보는지 보기")
    void ratioCheckShouldRespectTolerance() {
        Job1DatasetDefinition dataset = catalog.loadDataset("D3-pairwise");
        InputDimensions dimensions = catalog.loadInputDimensions();

        // 기대 비율대로 100명 구성
        List<Map<String, String>> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, String> user = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Double>> dimension : dataset.dimensionRatios().entrySet()) {
                user.put(dimension.getKey(), pick(dimension.getValue(), i));
            }
            users.add(user);
        }

        DatasetDistributionResult exact = new DatasetDistributionCheck()
                .compare(dataset, Map.of(), DimensionProfile.of(dimensions, users));
        assertThat(exact.ratioRows()).allMatch(DatasetDistributionResult.RatioRow::matched);

        // 사용량을 모두 below 로 바꾸면 usage 비율이 전부 틀린다
        users.forEach(user -> user.put("usage", "below"));
        DatasetDistributionResult skewed = new DatasetDistributionCheck()
                .compare(dataset, Map.of(), DimensionProfile.of(dimensions, users));
        assertThat(skewed.problems()).anyMatch(problem -> problem.startsWith("usage.below"));
        assertThat(skewed.problems()).anyMatch(problem -> problem.startsWith("pairwise coverage"));
    }

    @Test
    @DisplayName("D3, D4 비율이 차원마다 합 1 이고 정의된 값만 쓰는지 보기")
    void generatedDatasetsShouldDescribeEveryDimensionValue() {
        InputDimensions dimensions = catalog.loadInputDimensions();

        for (String datasetId : List.of("D3-pairwise", "D4-random")) {
            Job1DatasetDefinition dataset = catalog.loadDataset(datasetId);
            assertThat(dataset.distribution()).as("%s 는 비율로만 본다", datasetId).isNull();
            assertThat(dataset.minPairwiseCoverage()).isEqualTo(1.0);
            assertThat(dataset.dimensionRatios().keySet()).isEqualTo(dimensions.dimensions().keySet());

            dataset.dimensionRatios().forEach((name, ratios) -> {
                assertThat(ratios.keySet()).containsExactlyInAnyOrderElementsOf(dimensions.dimensions().get(name).values());
                assertThat(ratios.values().stream().mapToDouble(Double::doubleValue).sum())
                        .as("%s %s 비율 합", datasetId, name)
                        .isCloseTo(1.0, within(1e-9));
            });
            assertThat(catalog.resourceExists("reliability/datasets/" + datasetId + "/expected-summary.json")).isTrue();
        }
        assertThat(catalog.resourceExists("reliability/datasets/_generator/combination_seed.sql")).isTrue();
    }

    @Test
    @DisplayName("D2 경계값 경우가 겹치지 않고 기대 결과를 갖는지 보기")
    void boundaryCasesShouldBeUniqueAndHaveExpectations() {
        Job1DatasetDefinition dataset = catalog.loadDataset("D2-boundary");
        String seed = catalog.loadCheckText("reliability/datasets/D2-boundary/main-db/01_seed_d2.sql");

        Set<String> codes = new HashSet<>();
        for (JsonNode boundaryCase : dataset.boundaryCases()) {
            String code = boundaryCase.get("code").asText();
            assertThat(codes.add(code)).as("중복 경우 %s", code).isTrue();
            assertThat(boundaryCase.has("target")).as("%s target", code).isTrue();
            assertThat(boundaryCase.path("decision").asText()).isIn("current", "needs-decision");
            assertThat(seed).as("시드에 %s 가 있는지", code).contains("'" + code + "'");
        }
        assertThat(codes).hasSize(33);
    }

    // 누적 비율로 i 번째 사용자의 값을 고른다
    private String pick(Map<String, Double> ratios, int index) {
        double position = (index + 0.5) / 100.0;
        double cumulative = 0;
        String last = null;
        for (Map.Entry<String, Double> entry : ratios.entrySet()) {
            cumulative += entry.getValue();
            last = entry.getKey();
            if (position < cumulative) {
                return entry.getKey();
            }
        }
        return last;
    }
}

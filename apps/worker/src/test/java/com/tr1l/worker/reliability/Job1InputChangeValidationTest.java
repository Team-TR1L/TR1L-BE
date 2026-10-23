package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tr1l.worker.reliability.content.ContentValidation;
import com.tr1l.worker.reliability.content.ReferenceAmountComparison;
import com.tr1l.worker.reliability.content.SnapshotHashCollector;
import com.tr1l.worker.reliability.content.SnapshotHashes;
import com.tr1l.worker.reliability.invariant.CrossDbInvariantChecks;
import com.tr1l.worker.reliability.invariant.InvariantValidationResult;
import com.tr1l.worker.reliability.invariant.InvariantValidator;
import com.tr1l.worker.reliability.invariant.Job1StateCollector;
import com.tr1l.worker.reliability.invariant.Job1StateSnapshot;
import com.tr1l.worker.reliability.invariant.PostgresInvariantChecks;
import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;
import com.tr1l.worker.reliability.support.AllureEvidenceWriter;
import com.tr1l.worker.reliability.support.JsonResultWriter;
import com.tr1l.worker.reliability.support.ReliabilityDataSourceFactory;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Files;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// S-014R 장애와 재실행 사이에 main DB 원천이 바뀐 뒤 재실행
//   하네스가 바꾼 사용자 목록을 input-change.json 으로 남긴다 (무리: CALCULATED, LOADED, NOT_LOADED)
//   규칙 (TR1L-179 에서 정함)
//     - 재실행 사이에 원천이 바뀌어도 처음 적재한 값으로 청구한다. CALCULATED, LOADED 는 S-001 과 같은 청구서여야 한다
//       NOT_LOADED 는 재실행 때 처음 적재되므로 바뀐 값으로 청구되는 것이 규칙대로다
//     - 정산 월 첫 실행 때 청구 대상이던 사용자는 그 뒤 탈퇴해도 청구한다. 누구도 NOT_BILLED 이면 안 된다
//     - 청구서와 billing_targets 가 어긋나면 안 된다 (참조 계산 C6)
//   그 밖에 구조(invariant, 남은 work 없음, 마감)와 바뀌지 않은 사용자의 청구서가 S-001 과 같은지 본다
class Job1InputChangeValidationTest {
    static final String INPUT_CHANGE = "input-change.json";
    static final String INPUT_CHANGE_CHECK = "input-change-check.json";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();

    @Test
    @DisplayName("S-014R 재실행 전에 원천이 바뀌어도 처음 적재한 값과 처음 명단으로 청구하는지 보기")
    void rerunAfterInputChangeShouldFollowCutoff() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-014R");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + System.getenv("JOB1_INPUT_CHANGE"));
        InputChange change = ReliabilityObjectMappers.json().readValue(
                Files.readString(resultWriter.scenarioDirectory(scenario).resolve(INPUT_CHANGE)), InputChange.class);
        SnapshotHashes baseline = ReliabilityObjectMappers.json().readValue(
                Files.readString(resultWriter.scenarioDirectory(catalog.loadScenario("S-001"))
                        .resolve(ContentValidation.BASELINE_HASHES)),
                SnapshotHashes.class);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            Job1StateSnapshot snapshot =
                    new Job1StateCollector(jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection())
                            .collect(billingMonthDay);
            InvariantValidationResult validationResult = new InvariantValidator(
                    new PostgresInvariantChecks(jdbcTemplate, catalog),
                    new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
            ).validate(scenario, catalog.loadActiveInvariants());
            SnapshotHashes current = new SnapshotHashCollector(mongoClient, config.mongoDatabase(), config.snapshotCollection())
                    .collect(billingMonthDay);
            // 참조 계산(C6)은 지금 billing_targets 로 다시 계산한다. 청구서와 billing_targets 가 어긋났는지 보는 데 쓴다
            ReferenceAmountComparison amounts =
                    new ContentValidation(jdbcTemplate, mongoClient, config, catalog, resultWriter, evidenceWriter)
                            .compareReferenceAmounts(scenario, billingMonthDay);
            String cycleStatus = cycleStatus(jdbcTemplate, billingMonthDay);

            // 바뀐 사용자: 무리별로 S-001 과 같은지, 다른지, 청구되지 않았는지
            Set<Long> changed = new HashSet<>();
            Map<String, Map<Outcome, Integer>> byGroup = new LinkedHashMap<>();
            change.groups().forEach((group, users) -> {
                Map<Outcome, Integer> outcomes = new TreeMap<>();
                for (Long user : users) {
                    changed.add(user);
                    outcomes.merge(outcome(baseline.hashes().get(user), current.hashes().get(user)), 1, Integer::sum);
                }
                byGroup.put(group, outcomes);
            });
            // 바뀌지 않은 사용자: S-001 과 모두 같아야 한다
            long unchangedDiffs = baseline.hashes().entrySet().stream()
                    .filter(entry -> !changed.contains(entry.getKey()))
                    .filter(entry -> !Objects.equals(entry.getValue(), current.hashes().get(entry.getKey())))
                    .count();
            // 처음 적재된 뒤에는 바뀌면 안 되고(CALCULATED, LOADED), 아무도 빠지면 안 된다
            long ruleViolations = byGroup.entrySet().stream()
                    .flatMap(group -> group.getValue().entrySet().stream()
                            .filter(entry -> entry.getKey() == Outcome.NOT_BILLED
                                    || (entry.getKey() == Outcome.CHANGED && !"NOT_LOADED".equals(group.getKey()))))
                    .mapToLong(Map.Entry::getValue)
                    .sum()
                    + amounts.mismatchedUsers();

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("change", change.change());
            metrics.put("groups", byGroup);
            metrics.put("unchangedDiffs", unchangedDiffs);
            metrics.put("referenceMismatchedUsers", amounts.mismatchedUsers());
            metrics.put("cycleStatus", cycleStatus);
            Check check = new Check("CUTOFF_HOLD", ruleViolations == 0, ruleViolations,
                    change.change() + " " + change.title() + " → " + describe(byGroup)
                            + ", 청구서와 billing_targets 불일치 " + amounts.mismatchedUsers() + "명",
                    metrics);
            resultWriter.write(scenario, "current-state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            resultWriter.write(scenario, INPUT_CHANGE_CHECK, check);
            evidenceWriter.attachJson(INPUT_CHANGE_CHECK, check);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(snapshot.processingCount() + snapshot.targetCount() + snapshot.failedCount())
                    .as("끝나지 않은 work").isZero();
            assertThat(cycleStatus).isEqualTo("FINISHED");
            assertThat(unchangedDiffs).as("원천이 바뀌지 않은 사용자의 청구서가 S-001 과 다름").isZero();
            assertThat(ruleViolations).as("처음 적재한 값, 처음 명단으로 청구하지 않음 %s", byGroup).isZero();
        }
    }

    private Outcome outcome(String baseline, String current) {
        if (current == null) {
            return baseline == null ? Outcome.SAME : Outcome.NOT_BILLED;
        }
        return current.equals(baseline) ? Outcome.SAME : Outcome.CHANGED;
    }

    private String describe(Map<String, Map<Outcome, Integer>> byGroup) {
        StringBuilder text = new StringBuilder();
        byGroup.forEach((group, outcomes) -> {
            if (outcomes.isEmpty()) {
                return;
            }
            if (!text.isEmpty()) {
                text.append(" / ");
            }
            text.append(group).append(' ').append(outcomes);
        });
        return text.toString();
    }

    private String cycleStatus(NamedParameterJdbcTemplate jdbcTemplate, LocalDate billingMonthDay) {
        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM billing_cycle WHERE billing_month = :billingMonth",
                new MapSqlParameterSource("billingMonth", Date.valueOf(billingMonthDay)),
                String.class);
        return status.isEmpty() ? "NONE" : status.get(0);
    }

    private void assumeLocalReliabilityEnabled() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 input change validation"
        );
    }

    // SAME S-001 과 같은 청구서, CHANGED 다른 청구서, NOT_BILLED 청구되지 않음
    enum Outcome { SAME, CHANGED, NOT_BILLED }

    // 하네스가 남긴 바뀐 사용자 목록
    record InputChange(String change, String title, Map<String, List<Long>> groups) {
    }

    // 하네스가 check_result 에 옮겨 적는 결과
    record Check(String checkId, boolean passed, long violationCount, String detail, Map<String, Object> metrics) {
    }
}

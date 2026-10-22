package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tr1l.worker.reliability.fault.FaultPointExpectation;
import com.tr1l.worker.reliability.fault.FaultPoints;
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
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// 장애 행렬 한 칸: S-010 장애 직후 상태 확인 뒤 S-010R 수렴 확인
// 지점은 JOB1_FAULT_POINT 로 받는다 (하네스가 worker 에 넘긴 값과 같다)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Job1FaultMatrixValidationTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final Job1RerunConvergence convergence = new Job1RerunConvergence(catalog, resultWriter, evidenceWriter);
    private final FaultPointExpectation expectation = new FaultPointExpectation();

    @Test
    @Order(1)
    @DisplayName("S-010 장애 직후 지점별 예상 상태와 중복 없음 보기")
    void afterFaultShouldMatchExpectedStateOfPoint() throws Exception {
        assumeLocalReliabilityEnabled();

        String point = System.getenv("JOB1_FAULT_POINT");
        assertThat(point).as("JOB1_FAULT_POINT").isNotBlank();
        FaultPoints.Point definition = catalog.loadFaultPoints().points().get(point);
        assertThat(definition).as("fault point %s", point).isNotNull();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-010");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + point + " " + System.getenv("JOB1_FAULT_TIMING")
                + " " + System.getenv("JOB1_FAULT_KIND"));
        evidenceWriter.attachJson("scenario.json", scenario);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));

            Job1StateSnapshot snapshot = collector(jdbcTemplate, mongoClient, config)
                    .collect(LocalDate.parse(scenario.job().billingMonthDay()));
            InvariantValidationResult validationResult = validator(jdbcTemplate, mongoClient, config)
                    .validate(scenario, catalog.loadActiveInvariants());
            // 장애 종류(K1 ~ K4)에 따라 장애 직후 상태가 다를 수 있다
            String kind = System.getenv().getOrDefault("JOB1_FAULT_KIND", "K1");
            FaultPointExpectation.Result expected =
                    expectation.evaluate(point + " " + kind, definition.expectFor(kind), snapshot, targetUsers(config));

            resultWriter.write(scenario, "fault-state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            resultWriter.write(scenario, "fault-expectation.json", expected);
            evidenceWriter.attachJson("fault-state-snapshot.json", snapshot);
            evidenceWriter.attachJson("fault-expectation.json", expected);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(expected.matched()).as("fault expectation %s", expected).isTrue();
        }
    }

    @Test
    @Order(2)
    @DisplayName("S-010R 장애 뒤 같은 cutoff 로 다시 돌리면 S-001 run 직후 상태와 내용으로 돌아오는지 보기")
    void rerunAfterFaultShouldConvergeToS001RunState() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-010R");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + System.getenv("JOB1_FAULT_POINT"));

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            convergence.verify(scenario, jdbcTemplate, mongoClient, config);
        }
    }

    // 청구 대상 사용자 수 (main 디비 기준)
    private long targetUsers(ReliabilityRuntimeConfig config) {
        Long count = new NamedParameterJdbcTemplate(dataSourceFactory.createMainDataSource(config)).getJdbcTemplate()
                .queryForObject("""
                        SELECT count(*)
                        FROM users
                        WHERE user_role = 'USER' AND user_status = 'ACTIVE' AND plan_code IS NOT NULL
                        """, Long.class);
        return count == null ? 0 : count;
    }

    private Job1StateCollector collector(NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                                         ReliabilityRuntimeConfig config) {
        return new Job1StateCollector(jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection());
    }

    private InvariantValidator validator(NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                                         ReliabilityRuntimeConfig config) {
        return new InvariantValidator(
                new PostgresInvariantChecks(jdbcTemplate, catalog),
                new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
        );
    }

    private void assumeLocalReliabilityEnabled() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 fault matrix validation"
        );
    }
}

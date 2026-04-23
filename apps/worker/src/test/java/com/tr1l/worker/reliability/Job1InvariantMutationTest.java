package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.invariant.InvariantResult;
import com.tr1l.worker.reliability.invariant.PostgresInvariantChecks;
import com.tr1l.worker.reliability.support.ReliabilityDataSourceFactory;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// INV-004 mutation vertical slice
class Job1InvariantMutationTest {
    private static final String ORIGINAL_PATH =
            "reliability/invariants/active/INV-004-target-work-membership-match.json";
    private static final String MUTANT_PATH =
            "reliability/invariants/mutants/MUT-INV-004-loose-count-gap.json";
    private static final long ORPHAN_USER_ID = 90_000_001L;

    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();

    @Test
    @DisplayName("INV-004 는 단건 불일치를 잡고 느슨한 mutant 는 놓치는지 보기")
    void originalInvariantShouldCatchSingleMismatchThatLooseMutantMisses() throws Exception {
        assumeLocalReliabilityEnabled();

        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        NamedParameterJdbcTemplate jdbcTemplate =
                new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
        PostgresInvariantChecks checks = new PostgresInvariantChecks(jdbcTemplate, catalog);

        InvariantDefinition original = loader.loadJson(ORIGINAL_PATH, InvariantDefinition.class);
        InvariantDefinition mutant = loader.loadJson(MUTANT_PATH, InvariantDefinition.class);
        LocalDate billingMonthDay = LocalDate.parse("2026-01-01");

        try {
            cleanupOrphanWork(jdbcTemplate, billingMonthDay);

            InvariantResult originalBefore = checks.execute(original, billingMonthDay);
            InvariantResult mutantBefore = checks.execute(mutant, billingMonthDay);

            insertOrphanWork(jdbcTemplate, billingMonthDay);

            InvariantResult originalAfter = checks.execute(original, billingMonthDay);
            InvariantResult mutantAfter = checks.execute(mutant, billingMonthDay);

            writeMutationReport(originalBefore, mutantBefore, originalAfter, mutantAfter);

            assertThat(originalBefore.passed()).isTrue();
            assertThat(mutantBefore.passed()).isTrue();
            assertThat(originalAfter.passed()).isFalse();
            assertThat(originalAfter.violationCount()).isEqualTo(1);
            assertThat(mutantAfter.passed()).isTrue();
            assertThat(mutantAfter.violationCount()).isZero();
        } finally {
            cleanupOrphanWork(jdbcTemplate, billingMonthDay);
        }
    }

    // 로컬 reliability 실행 조건
    private void assumeLocalReliabilityEnabled() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 mutation validation"
        );
    }

    // 단건 고아 work 주입
    private void insertOrphanWork(NamedParameterJdbcTemplate jdbcTemplate, LocalDate billingMonthDay) {
        jdbcTemplate.update(
                """
                        INSERT INTO billing_work (
                            billing_month_day,
                            user_id,
                            status,
                            attempt_count
                        )
                        VALUES (
                            :billingMonthDay,
                            :userId,
                            'TARGET',
                            0
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("billingMonthDay", billingMonthDay)
                        .addValue("userId", ORPHAN_USER_ID)
        );
    }

    // 테스트 데이터 정리
    private void cleanupOrphanWork(NamedParameterJdbcTemplate jdbcTemplate, LocalDate billingMonthDay) {
        jdbcTemplate.update(
                """
                        DELETE FROM billing_work
                        WHERE billing_month_day = :billingMonthDay
                          AND user_id = :userId
                        """,
                new MapSqlParameterSource()
                        .addValue("billingMonthDay", billingMonthDay)
                        .addValue("userId", ORPHAN_USER_ID)
        );
    }

    // mutation 결과 저장
    private void writeMutationReport(
            InvariantResult originalBefore,
            InvariantResult mutantBefore,
            InvariantResult originalAfter,
            InvariantResult mutantAfter
    ) throws Exception {
        Path output = Paths.get(
                "build",
                "job1-reliability-results",
                "mutation",
                "INV-004-vs-MUT-INV-004",
                "mutation-result.json"
        );
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                ReliabilityObjectMappers.json().writeValueAsString(Map.of(
                        "mutationTarget", "INV-004",
                        "mutantId", "MUT-INV-004",
                        "injectedMismatchCount", 1,
                        "originalBefore", originalBefore,
                        "mutantBefore", mutantBefore,
                        "originalAfter", originalAfter,
                        "mutantAfter", mutantAfter,
                        "interpretation", "원본은 단건 불일치를 잡지만 느슨한 mutant 는 5건 이하 불일치를 놓침"
                )),
                StandardCharsets.UTF_8
        );
    }
}

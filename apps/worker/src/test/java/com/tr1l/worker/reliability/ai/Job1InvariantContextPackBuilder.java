package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;

import java.util.List;

// Job1 컨텍스트 묶음 조립
public final class Job1InvariantContextPackBuilder {
    private static final String BASE_PATH = "reliability/ai/context/job1/";

    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();

    public Job1InvariantContextPack build() {
        return new Job1InvariantContextPack(
                "job1-invariant-generator-mvp",
                List.of(
                        load("job1-billing-targets-ddl", "billing_targets 테이블 DDL", "01_billing_targets_ddl.sql"),
                        load("job1-billing-work-ddl", "billing_work 테이블 DDL", "02_billing_work_ddl.sql"),
                        load("job1-billing-snapshot-schema", "Mongo billing_snapshot 스키마", "03_billing_snapshot_schema.json"),
                        load("job1-step3-core-flow", "Step3 핵심 코드", "04_step3_core_flow.txt"),
                        load("job1-state-transitions", "상태 전이 규칙", "05_state_transitions.txt"),
                        load("job1-rerun-design-intent", "rerun safe 설계 의도", "06_rerun_design_intent.txt")
                )
        );
    }

    // classpath 리소스 적재
    private ContextSource load(String id, String title, String fileName) {
        String classpathLocation = BASE_PATH + fileName;
        return new ContextSource(
                id,
                title,
                classpathLocation,
                loader.loadText(classpathLocation)
        );
    }
}

package com.tr1l.worker.config.job;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.FlowExecutor;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Job1 흐름 전이
// JobRepository 없이 Step 결과만 흉내 내서 어느 Step 까지 가는지 본다
class CalculateJobFlowTest {

    @Test
    void gateFailureStopsJobBeforeFlatten() throws Exception {
        FakeExecutor executor = new FakeExecutor(Map.of("gate", "FAILED"));

        FlowExecution execution = flow().start(executor);

        assertThat(executor.executed).containsExactly("gate");
        assertThat(execution.getStatus()).isEqualTo(FlowExecutionStatus.FAILED);
    }

    @Test
    void gateNoopEndsJobWithoutProcessing() throws Exception {
        FakeExecutor executor = new FakeExecutor(Map.of("gate", "NOOP"));

        FlowExecution execution = flow().start(executor);

        assertThat(executor.executed).containsExactly("gate");
        assertThat(execution.getStatus()).isEqualTo(FlowExecutionStatus.COMPLETED);
    }

    @Test
    void gateCompletedRunsEveryStep() throws Exception {
        FakeExecutor executor = new FakeExecutor(Map.of());

        FlowExecution execution = flow().start(executor);

        assertThat(executor.executed).containsExactly("gate", "flatten", "target", "calculate", "finalize");
        assertThat(execution.getStatus()).isEqualTo(FlowExecutionStatus.COMPLETED);
    }

    private Flow flow() {
        return CalculateJobConfig.calculateJobFlow(
                step("gate"), step("flatten"), step("target"), step("calculate"), step("finalize"));
    }

    private Step step(String name) {
        Step step = mock(Step.class);
        when(step.getName()).thenReturn(name);
        return step;
    }

    // Step 이름별 종료 코드를 돌려주는 실행기. 정하지 않은 Step 은 COMPLETED
    private static final class FakeExecutor implements FlowExecutor {
        private final Map<String, String> exitCodes;
        private final List<String> executed = new ArrayList<>();
        private final JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();

        private FakeExecutor(Map<String, String> exitCodes) {
            this.exitCodes = exitCodes;
        }

        @Override
        public String executeStep(Step step) {
            executed.add(step.getName());
            return exitCodes.getOrDefault(step.getName(), "COMPLETED");
        }

        @Override
        public JobExecution getJobExecution() {
            return jobExecution;
        }

        @Override
        public StepExecution getStepExecution() {
            return null;
        }

        @Override
        public void close(FlowExecution result) {
        }

        @Override
        public void abandonStepExecution() {
        }

        @Override
        public void updateJobExecutionStatus(FlowExecutionStatus status) {
        }

        @Override
        public boolean isRestart() {
            return false;
        }

        @Override
        public void addExitStatus(String code) {
        }
    }
}

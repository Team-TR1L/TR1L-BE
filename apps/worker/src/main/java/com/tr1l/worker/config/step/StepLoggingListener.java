package com.tr1l.worker.config.step;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StepLoggingListener implements StepExecutionListener {
    private static final String START_TS_KEY = "stepStartTs";
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final ThreadLocal<Span> stepSpan = new ThreadLocal<>();
    private final ThreadLocal<Scope> stepScope = new ThreadLocal<>();

    public StepLoggingListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.tracer = GlobalOpenTelemetry.getTracer("batch");
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putString(START_TS_KEY, Instant.now().toString());

        log.info("[BATCH][STEP][START] job={} step={} execId={} params={}",
                stepExecution.getJobExecution().getJobInstance().getJobName(),
                stepExecution.getStepName(),
                stepExecution.getId(),
                stepExecution.getJobParameters());

        MDC.put("batch.job", stepExecution.getJobExecution().getJobInstance().getJobName());
        MDC.put("batch.step", stepExecution.getStepName());

        Span span = tracer.spanBuilder("batch.step")
                .setAttribute("job", stepExecution.getJobExecution().getJobInstance().getJobName())
                .setAttribute("step", stepExecution.getStepName())
                .setAttribute("execution.id", stepExecution.getId())
                .startSpan();
        stepSpan.set(span);
        stepScope.set(span.makeCurrent());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String startTs = stepExecution.getExecutionContext().getString(START_TS_KEY, null);

        long tookMs = -1;
        if (startTs != null) {
            tookMs = Duration.between(Instant.parse(startTs), Instant.now()).toMillis();
        }

        log.info("[BATCH][STEP][END] job={} step={} execId={} status={} exit={} read={} write={} skip(R/W/P)={}/{}/{} commit={} rollback={} tookMs={}",
                stepExecution.getJobExecution().getJobInstance().getJobName(),
                stepExecution.getStepName(),
                stepExecution.getId(),
                stepExecution.getStatus(),
                stepExecution.getExitStatus().getExitCode(),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getReadSkipCount(),
                stepExecution.getWriteSkipCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getCommitCount(),
                stepExecution.getRollbackCount(),
                tookMs);

        if (tookMs >= 0) {
            Timer.builder("batch.step.duration")
                    .tag("job", stepExecution.getJobExecution().getJobInstance().getJobName())
                    .tag("step", stepExecution.getStepName())
                    .tag("status", stepExecution.getStatus().name())
                    .register(meterRegistry)
                    .record(tookMs, TimeUnit.MILLISECONDS);
        }

        Span span = stepSpan.get();
        Scope scope = stepScope.get();
        if (scope != null) {
            scope.close();
        }
        if (span != null) {
            if (stepExecution.getStatus().isUnsuccessful()) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }
        stepSpan.remove();
        stepScope.remove();
        MDC.remove("batch.job");
        MDC.remove("batch.step");

        return stepExecution.getExitStatus();
    }
}

package com.tr1l.worker.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


@Component
@Slf4j
public class StepMetricsListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("  ↪️ [STEP START] name={} id={} jobExecutionId={}",
                stepExecution.getStepName(),
                stepExecution.getId(),
                stepExecution.getJobExecutionId()
        );
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        BatchStatus status = stepExecution.getStatus();
        ExitStatus exitStatus = stepExecution.getExitStatus();

        LocalDateTime startTime = stepExecution.getStartTime();
        LocalDateTime endTime = stepExecution.getEndTime();

        Instant start = startTime != null ? startTime.atZone(ZoneId.systemDefault()).toInstant() : null;
        Instant end = endTime != null ? endTime.atZone(ZoneId.systemDefault()).toInstant() : null;

        long durationMs = (start != null && end != null)
                ? Duration.between(start, end).toMillis()
                : -1;

        log.info(
                "  ✅ [STEP END] name={} id={} status={} exit={} durationMs={} " +
                        "read={} write={} commit={} rollback={} " +
                        "readSkip={} processSkip={} writeSkip={}",
                stepExecution.getStepName(),
                stepExecution.getId(),
                status,
                exitStatus != null ? exitStatus.getExitCode() : "N/A",
                durationMs,
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getCommitCount(),
                stepExecution.getRollbackCount(),
                stepExecution.getReadSkipCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getWriteSkipCount()
        );

        if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
            for (Throwable t : stepExecution.getFailureExceptions()) {
                log.error("  💥 [STEP ERROR] name={} msg={}", stepExecution.getStepName(), t.getMessage(), t);
            }
        }

        return exitStatus;
    }


}

package com.tr1l.worker.config.step;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class StepLoggingListener implements StepExecutionListener {
private static final String START_TS_KEY = "stepStartTs";

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putString(START_TS_KEY, Instant.now().toString());

        log.info("[BATCH][STEP][START] job={} step={} execId={} params={}",
                stepExecution.getJobExecution().getJobInstance().getJobName(),
                stepExecution.getStepName(),
                stepExecution.getId(),
                stepExecution.getJobParameters());
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

        return stepExecution.getExitStatus();
    }
}

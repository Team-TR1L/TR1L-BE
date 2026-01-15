package com.tr1l.worker.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;

/**
 *
 ==========================
 *$method$
 *
 * @parm $parm
 * @return
 * @author 구본문
 * @version 1.0.0
 * @date 2026-1-15
 * ========================== */

@Slf4j
@Component
public class BatchLoggingListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution){
        String jobName = jobExecution.getJobInstance().getJobName();
        Map<String, JobParameter<?>> paramsMap = jobExecution.getJobParameters().getParameters();
        Properties params = new Properties();
        paramsMap.forEach((k, v) -> params.put(k, v.getValue()));

        log.info("▶️ [JOB START] name={} id={} params={}",
                jobName,
                jobExecution.getId(),
                params
        );
    }


    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus batchStatus = jobExecution.getStatus();
        ExitStatus exitStatus = jobExecution.getExitStatus();

        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();

        Instant start = startTime != null ? startTime.atZone(ZoneId.systemDefault()).toInstant() : null;
        Instant end = endTime != null ? endTime.atZone(ZoneId.systemDefault()).toInstant() : null;

        long durationMs = (start != null && end != null)
                ? Duration.between(start, end).toMillis()
                : -1;

        log.info("✅ [JOB END] name={} id={} status={} exit={} durationMs={}",
                jobName,
                jobExecution.getId(),
                batchStatus,
                exitStatus != null ? exitStatus.getExitCode() : "N/A",
                durationMs
        );

        if (jobExecution.getAllFailureExceptions() != null && !jobExecution.getAllFailureExceptions().isEmpty()) {
            for (Throwable t : jobExecution.getAllFailureExceptions()) {
                log.error("💥 [JOB ERROR] name={} id={} msg={}", jobName, jobExecution.getId(), t.getMessage(), t);
            }
        }
    }
}

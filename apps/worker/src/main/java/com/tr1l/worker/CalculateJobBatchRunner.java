package com.tr1l.worker;

import com.tr1l.worker.config.BatchJobConfiguration;
import com.tr1l.worker.config.TimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class CalculateJobBatchRunner implements CommandLineRunner {
    private final ApplicationContext applicationContext;

    private final JobLauncher jobLauncher;
    private final BatchJobConfiguration batchConfiguration;
    private final TimeProperties timeProperties;

    private final ZoneId zoneId;
    private final DateTimeFormatter formatter;

    public CalculateJobBatchRunner(
            ApplicationContext applicationContext,
            JobLauncher jobLauncher,
            BatchJobConfiguration batchConfiguration,
            TimeProperties timeProperties
    ) {
        this.applicationContext = applicationContext;
        this.jobLauncher = jobLauncher;
        this.batchConfiguration = batchConfiguration;
        this.timeProperties = timeProperties;

        this.zoneId = ZoneId.of(timeProperties.zone());
        this.formatter = DateTimeFormatter.ofPattern(timeProperties.format());
    }


    @Override
    public void run(String... args) throws Exception {
        String jobName = batchConfiguration.getJobName();
        log.info("Resolved spring.batch.job.name='{}'", batchConfiguration.getJobName());

        switch (jobName) {
            case "calculateJob":
                runCalculateJob();
                break;
            case "formatJob":
                runFormatJob();
                break;
            default:
                throw new IllegalArgumentException("unknown job name!");
        }
    }

    /**
     * Job1 실행
     */
    private void runCalculateJob() throws Exception {

        LocalDateTime now = LocalDateTime.now(zoneId);
        Instant cutoffAt = batchConfiguration.getJob1StartTimeAsInstant();
        String channelOrder = batchConfiguration.getChannelOrder();

        log.info("""
                        ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                        ┃                 BATCH JOB START                  ┃
                        ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
                        ┃ Job Name    : CalculateJob(Job1)
                        ┃ Started At  : {} (KST)
                        ┃ channelOrder: {}
                        ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                        """,
                now.format(formatter),
                channelOrder
        );

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("cutoff", cutoffAt.toString())
                .addString("channelOrder", batchConfiguration.getChannelOrder())
                .toJobParameters();


        Job calcaulateJob = applicationContext.getBean("calculateJob", Job.class);
        JobExecution execution = jobLauncher.run(calcaulateJob, jobParameters);

        handleJobCompletion(execution,"CalculateJob");
    }

    /**
     * Job2 실행
     */
    private void runFormatJob() throws Exception {

        LocalDateTime now = LocalDateTime.now(zoneId);
        Instant cutoffAt = batchConfiguration.getJob2StartTimeAsInstant();

        log.info("""
                        ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                        ┃                 BATCH JOB START                  ┃
                        ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
                        ┃ Job Name    : FormatJob(Job2)
                        ┃ Started At  : {} (KST)
                        ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                        """,
                now.format(formatter)
        );

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("cutoff", cutoffAt.toString())
                .addString("channelOrder", batchConfiguration.getChannelOrder())
                .toJobParameters();


        Job formatJob = applicationContext.getBean("formatJob", Job.class);
        JobExecution execution=jobLauncher.run(formatJob, jobParameters);

        handleJobCompletion(execution,"FormatJob");
    }


    /**
     * Job 완료 처리 및 Exit Code 설정
     */
    private void handleJobCompletion(JobExecution execution, String jobName) {
        log.info("""
                        ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                        ┃                 BATCH JOB END                    ┃
                        ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
                        ┃ Job Name    : {}
                        ┃ End At      : {} (KST)
                        ┃ Job Status  : {}
                        ┃ Exit Status : {}
                        ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                        """,
                jobName,
                LocalDateTime.now(), execution.getStatus(), execution.getExitStatus());

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            //배치 정상적 종료 시
            System.exit(0);
        } else {
            //배치 비정상적 종료
            System.exit(1);
        }
    }
}


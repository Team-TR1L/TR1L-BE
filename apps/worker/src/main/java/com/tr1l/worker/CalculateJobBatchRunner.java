package com.tr1l.worker;

import com.tr1l.worker.config.BatchJobConfiguration;
import com.tr1l.worker.config.TimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.IntConsumer;

@Component
@Slf4j
public class CalculateJobBatchRunner implements CommandLineRunner {
    // 이미 실행 중인 job 이 있어 시작하지 않았을 때 종료 코드. 성공(0), 실패(1)와 구분한다
    static final int SKIPPED_EXIT_CODE = 2;

    private final ApplicationContext applicationContext;

    private final JobLauncher jobLauncher;
    private final BatchJobConfiguration batchConfiguration;
    private final TimeProperties timeProperties;

    private final ZoneId zoneId;
    private final DateTimeFormatter formatter;
    private final boolean exitOnComplete;
    private final boolean addRunId;
    private final IntConsumer exit;

    @Autowired
    public CalculateJobBatchRunner(
            ApplicationContext applicationContext,
            JobLauncher jobLauncher,
            BatchJobConfiguration batchConfiguration,
            TimeProperties timeProperties,
            @Value("${BATCH_EXIT_ON_COMPLETE:true}") boolean exitOnComplete,
            @Value("${BATCH_ADD_RUN_ID:false}") boolean addRunId
    ) {
        this(applicationContext, jobLauncher, batchConfiguration, timeProperties, exitOnComplete, addRunId, System::exit);
    }

    CalculateJobBatchRunner(
            ApplicationContext applicationContext,
            JobLauncher jobLauncher,
            BatchJobConfiguration batchConfiguration,
            TimeProperties timeProperties,
            boolean exitOnComplete,
            boolean addRunId,
            IntConsumer exit
    ) {
        this.exit = exit;
        this.applicationContext = applicationContext;
        this.jobLauncher = jobLauncher;
        this.batchConfiguration = batchConfiguration;
        this.timeProperties = timeProperties;
        this.exitOnComplete = exitOnComplete;
        this.addRunId = addRunId;

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

        // calculateJob 은 실행마다 run.id 를 붙여 새 JobInstance 로 시작한다 (TR1L-177)
        // 같은 cutoff 를 같은 JobInstance 로 돌리면 강제 종료된 실행이 STARTED 로 남아 재실행이 시작하지 못한다
        // 같은 cutoff 의 중복 처리는 billing_cycle 마감, billing_work claim 과 lease 가 막는다
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("cutoff", cutoffAt.toString())
                .addString("channelOrder", batchConfiguration.getChannelOrder())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();


        Job calcaulateJob = applicationContext.getBean("calculateJob", Job.class);
        try {
            JobExecution execution = jobLauncher.run(calcaulateJob, jobParameters);
            handleJobCompletion(execution,"CalculateJob");
        } catch (JobExecutionAlreadyRunningException e) {
            // root 로그 수준이 ERROR 라 WARN 이면 남지 않는다. 시작하지 않았으면 성공으로 끝내지 않는다
            log.error("Job already running, skipping start. job=calculateJob err={}", e.getMessage());
            exit.accept(SKIPPED_EXIT_CODE);
        }
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

        JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                .addString("cutoff", cutoffAt.toString());
        if (addRunId) {
            paramsBuilder.addLong("run.id", System.currentTimeMillis());
        }
        JobParameters jobParameters = paramsBuilder.toJobParameters();


        Job formatJob = applicationContext.getBean("formatJob", Job.class);
        try {
            JobExecution execution=jobLauncher.run(formatJob, jobParameters);
            handleJobCompletion(execution,"FormatJob");
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("Job already running, skipping start. job=formatJob err={}", e.getMessage());
        }
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
            exit.accept(0);
        } else {
            //배치 비정상적 종료
            exit.accept(1);
        }
    }
}


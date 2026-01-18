package com.tr1l.worker.config;

import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import com.tr1l.billing.application.service.IssueBillingService;
import com.tr1l.worker.batch.calculatejob.step.step3.CalculateAndSnapshotWriter;
import com.tr1l.worker.batch.calculatejob.step.step3.WorkDocClaimReader;
import com.tr1l.worker.batch.calculatejob.step.step3.WorkToTargetRowProcessor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;

@Configuration
public class BillingStep3Config {

    @Bean
    public String workerId() {
        // ex) "12345@HOST"
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    @Bean
    public Step step3CalculateAndSnapshotStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager txManager,
            WorkDocClaimReader reader,
            WorkToTargetRowProcessor processor,
            CalculateAndSnapshotWriter writer,
            @Value("${app.billing.step3.chunk-size:200}") int chunkSize
    ) {
        return new StepBuilder("step3CalculateAndSnapshotStep", jobRepository)
                .<WorkDocClaimPort.ClaimedWorkDoc, WorkToTargetRowProcessor.WorkAndTargetRow>chunk(chunkSize, txManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public WorkDocClaimReader step3Reader(
            WorkDocClaimPort claimPort,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingYearMonth, // 변환 필요
            @Value("${app.billing.step3.fetch-size:200}") int fetchSize, // reader 반복 횟수, 선점 버퍼 단위
            @Value("${app.billing.step3.lease-seconds:1000}") long leaseSeconds, // 몇초 이후 target으로 회수할지, 트랜잭션 커밋 단위
            String workerId
    ) {
        YearMonth billingMonth = YearMonth.parse(billingYearMonth); // YYYY-MM -> YearMonth로 변환함

        return new WorkDocClaimReader(
                claimPort,
                billingMonth, //  YearMonth YYYY-MM
                fetchSize,
                Duration.ofSeconds(leaseSeconds),
                workerId
        );
    }

    @Bean
    @StepScope
    public WorkToTargetRowProcessor step3Processor( // 실제 계산
            BillingTargetLoadPort loadPort,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingYearMonth
    ) {
        YearMonth billingMonth = YearMonth.parse(billingYearMonth); // YYYY-MM -> YearMonth로 변환함
        return new WorkToTargetRowProcessor(loadPort, billingMonth);
    }

    @Bean
    public CalculateAndSnapshotWriter step3Writer( // MongDB 적재
            IssueBillingService issueBillingService,
            WorkDocStatusPort statusPort
    ) {
        return new CalculateAndSnapshotWriter(issueBillingService, statusPort);
    }
}



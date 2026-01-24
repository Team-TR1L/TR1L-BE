package com.tr1l.worker.config.step;

import com.tr1l.billing.application.model.WorkAndTargetRow;
import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import com.tr1l.billing.application.service.IssueBillingService;
import com.tr1l.worker.batch.calculatejob.step.step3.CalculateAndSnapshotWriter;
import com.tr1l.worker.batch.calculatejob.step.step3.WorkDocClaimAndTargetRowReader;
import com.tr1l.worker.batch.calculatejob.step.step3.CalculateBillingProcessor;
import com.tr1l.worker.batch.listener.PerfTimingListener;
import com.tr1l.worker.batch.listener.SqlQueryCountListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
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
import java.time.YearMonth;

/**
 * Job1 step3 설정
 * autor : 박준희
 * 2026-01-21
 */
@Configuration
public class BillingCalculateAndSnapshotConfig {

    // 추후 멀티스레드, 파티션 도입시 해당 id 유니크하게 변환 필요
    @Bean(name = "billingWorkerId")
    public String billingWorkerId() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    @Bean
    public Step billingCalculateAndSnapshotStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager txManager,
            WorkDocClaimAndTargetRowReader reader,
            CalculateBillingProcessor processor,
            CalculateAndSnapshotWriter writer,
            StepLoggingListener listener,
            MeterRegistry meterRegistry,
            @Value("${app.billing.step3.chunk-size:200}") int chunkSize
    ) {
        var perf = new PerfTimingListener<WorkAndTargetRow, CalculateBillingProcessor.Result>(
                30,
                80,
                500,
                1500,
                item -> item.work() == null ? "null" : item.work().id()
        );
        var sql = new SqlQueryCountListener(meterRegistry, "main", "target");
        return new StepBuilder("billingCalculateAndSnapshotStep", jobRepository)
                .<WorkAndTargetRow, CalculateBillingProcessor.Result>chunk(chunkSize, txManager)
                .reader(reader)
                .processor(processor)
                .listener(listener)
                .writer(writer)
                .listener((StepExecutionListener) perf)
                .listener((ChunkListener) perf)
                .listener((ItemReadListener<WorkAndTargetRow>) perf)
                .listener((ItemProcessListener<WorkAndTargetRow, CalculateBillingProcessor.Result>) perf)
                .listener((ItemWriteListener<CalculateBillingProcessor.Result>) perf)
                .listener((StepExecutionListener) sql)
                .listener((ChunkListener) sql)
                .build();
    }

    /**
     * 유저 선점 및 조회
     */
    @Bean
    @StepScope
    public WorkDocClaimAndTargetRowReader workDocClaimAndTargetRowReader(
            WorkDocClaimPort claimPort,
            BillingTargetLoadPort targetLoadPort,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingYearMonth,
            @Value("${app.billing.step3.fetch-size:200}") int fetchSize,
            @Value("${app.billing.step3.lease-seconds:1000}") long leaseSeconds,
            @Qualifier("billingWorkerId") String workerId
    ) {
        YearMonth billingMonth = YearMonth.parse(billingYearMonth); // YYYY-MM

        return new WorkDocClaimAndTargetRowReader(
                claimPort,
                targetLoadPort,
                billingMonth,
                fetchSize,
                Duration.ofSeconds(leaseSeconds),
                workerId
        );
    }

    /**
     * 계산 준비 / 변환
     * port로 변경 필요
     */
    @Bean
    @StepScope
    public CalculateBillingProcessor workToTargetRowProcessor(
            IssueBillingService issueBillingService
    ) {
        return new CalculateBillingProcessor(issueBillingService);
    }


    /**
     * 청구 데이터 발생 및 스냅샷 저장
     */
    @Bean
    @StepScope
    public CalculateAndSnapshotWriter calculateAndSnapshotWriter(
            BillingSnapshotSavePort snapshotSavePort,
            WorkDocStatusPort statusPort
    ) {
        return new CalculateAndSnapshotWriter(snapshotSavePort, statusPort);
    }
}

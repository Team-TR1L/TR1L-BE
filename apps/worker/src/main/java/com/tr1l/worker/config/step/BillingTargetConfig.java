package com.tr1l.worker.config.step;


import com.tr1l.billing.application.port.out.BillingWorkUpsertPort;
import com.tr1l.worker.batch.calculatejob.model.BillingTargetKey;
import com.tr1l.worker.batch.calculatejob.step.step2.BillingTargetReader;
import com.tr1l.worker.batch.calculatejob.step.step2.BillingTargetWriter;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ==========================
 * $method$
 * STEP 2 전체 Config
 *
 * @author $user
 * @version 1.0.0
 * @date $date
 * ==========================
 */
@Configuration
public class BillingTargetConfig {

    // step2 각 단계(reader, processor, writer)
    @Bean
    public Step billingTargetStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager transactionManager,
            BillingTargetReader reader,
            BillingTargetWriter writer,
            StepLoggingListener listener,
            MeterRegistry meterRegistry,
            @Qualifier("billingTargetTaskExecutor") TaskExecutor taskExecutor,
            @Value("${app.billing.step2.chunk-size}") int chunkSize
    )

    {
        var perf = new PerfTimingListener<BillingTargetKey, BillingTargetKey>(
                30,
                80,
                500,
                1500,
                item -> item.billingMonthDay() + ":" + item.userId(),
                meterRegistry,
                true,
                true
        );
        var sql = new SqlQueryCountListener(meterRegistry, "main", "target");
        return new StepBuilder("billingTargetStep", jobRepository)
                .<BillingTargetKey, BillingTargetKey>chunk(chunkSize, transactionManager)
                .reader(reader)
                .listener(listener)
                .writer(writer)
                .taskExecutor(taskExecutor)
                .listener((StepExecutionListener) perf)
                .listener((ChunkListener) perf)
                .listener((ItemReadListener<BillingTargetKey>) perf)
                .listener((ItemProcessListener<BillingTargetKey, BillingTargetKey>) perf)
                .listener((ItemWriteListener<BillingTargetKey>) perf)
                .listener((StepExecutionListener) sql)
                .listener((ChunkListener) sql)
                .build();
    }

    /**
     * ${app. .....: (기본 설정할 값) } 이렇게 되어있으면 yml 파일에 설정된 값을가져오고 없으면 기본 설정한 값으로 주입
     */

    // 뷰 테이블 이름, 집계 날짜, 페이지사이즈 파라미터값으로 넘겨준다.
    @Bean
    @StepScope
    public BillingTargetReader step2Reader(
            @Qualifier("targetDataSource") DataSource dataSource,
            @Value("${app.billing.targets-view-name:billing_targets}") String viewName,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingMonth,
            @Value("${app.billing.step2.page-size:1000}") int pageSize
    ) throws Exception {
        return new BillingTargetReader(dataSource, viewName, billingMonth, pageSize);
    }

    //port등록 해서 DB 주입
    @Bean
    public BillingTargetWriter step2Writer(BillingWorkUpsertPort billingWorkUpsertPort) {
        return new BillingTargetWriter(billingWorkUpsertPort);
    }

    //병렬 처리
    @Bean(name = "billingTargetTaskExecutor")
    public TaskExecutor billingTargetTaskExecutor(
            @Value("${app.billing.step2.pool-size}") int poolSize
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(0); // 큐 적재 대신 백프레셔
        executor.setThreadNamePrefix("job1-step2-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}

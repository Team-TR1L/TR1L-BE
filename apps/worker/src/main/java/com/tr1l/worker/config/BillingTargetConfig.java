package com.tr1l.worker.config;


import com.tr1l.billing.application.port.out.WorkDocUpsertPort;
import com.tr1l.worker.batch.calculatejob.model.BillingTargetKey;
import com.tr1l.worker.batch.calculatejob.model.WorkDoc;
import com.tr1l.worker.batch.calculatejob.step.step2.BillingTargetProcessor;
import com.tr1l.worker.batch.calculatejob.step.step2.BillingTargetReader;
import com.tr1l.worker.batch.calculatejob.step.step2.BillingTargetWriter;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 ==========================
 *$method$
 * STEP 2 전체 Config
 * @author $user
 * @version 1.0.0
 * @date $date
 * ========================== */
@Configuration
public class BillingTargetConfig {

    // step2 각 단계(reader, processor, writer)
    @Bean
    public Step step2ProduceWorkStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager transactionManager,
            BillingTargetReader reader,
            BillingTargetProcessor processor,
            BillingTargetWriter writer,
            @Value("${app.billing.step2.chunk-size:1000}") int chunkSize
    ) {
        return new StepBuilder("step2ProduceWorkStep", jobRepository)
                .<BillingTargetKey, WorkDoc>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
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
            @Value("${app.billing.targets-view-name:billing_targets_mv}") String viewName,
            @Value("#{jobParameters['billingMonth']}") String billingMonth,
            @Value("${app.billing.step2.page-size:1000}") int pageSize
    ) throws Exception {
        return new BillingTargetReader(dataSource, viewName, billingMonth,pageSize);
    }

    @Bean
    public BillingTargetProcessor step2Processor() {
        return new BillingTargetProcessor();
    }


    //port등록 해서 DB 주입
    @Bean
    public BillingTargetWriter step2Writer(WorkDocUpsertPort workDocUpsertPort) {
        return new BillingTargetWriter(workDocUpsertPort);
    }

}

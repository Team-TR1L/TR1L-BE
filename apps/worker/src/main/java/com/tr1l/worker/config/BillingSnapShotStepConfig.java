package com.tr1l.worker.config;


import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import com.tr1l.worker.batch.formatjob.domain.RenderedMessage;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapShotProcessor;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapShotReader;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.thymeleaf.TemplateEngine;

/**
 * ==========================
 * BillingSnapShotStepConfig
 *
 * BillingSnapShotStep들을 모아 step 연결하는곳
 * 
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */



@Configuration
public class BillingSnapShotStepConfig {

    private final MongoTemplate mongoTemplate;
    private final TemplateEngine templateEngine;

    public BillingSnapShotStepConfig(MongoTemplate mongoTemplate, TemplateEngine templateEngine) {
        this.mongoTemplate = mongoTemplate;
        this.templateEngine = templateEngine;
    }

    @Bean
    public Step billingSnapShotStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager transactionManager,
            BillingSnapShotReader reader,
            BillingSnapShotProcessor processor,
            //BillingSnapShotWriter writer,
            @Value("${app.billing.step2.chunk-size:1000}") int chunkSize
    ){
        return new StepBuilder("billingSnapShotStep",jobRepository)
                .<BillingSnapshotDoc, RenderedMessage>chunk(chunkSize,transactionManager)
                .reader(reader)
                .processor(processor)
                //.writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public BillingSnapShotReader billingSnapShotReader(
            MongoTemplate mongoTemplate,
            @Value("${app.format.snapshot-collection:billing_snapshot}") String collectionName,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingMonth,
            @Value("#{jobExecutionContext['onlyIssued'] ?: true}") boolean onlyIssued){
        return new BillingSnapShotReader(mongoTemplate,collectionName,billingMonth,onlyIssued);
    }

    @Bean
    public BillingSnapShotProcessor billingSnapShotProcessor (){
        return new BillingSnapShotProcessor(templateEngine);
    }

    /**
     * Writer 부분 작성해줘
     */
    /*
    public BillingSnapShotStepConfig billing....
     */
}

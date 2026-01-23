package com.tr1l.worker.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import com.tr1l.billing.application.port.out.S3UploadPort;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import com.tr1l.worker.batch.formatjob.domain.RenderedMessage;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapShotProcessor;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapShotWriter;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapshotKeysetReader;
import com.tr1l.worker.batch.listener.PerfTimingListener;
import org.springframework.batch.core.*;
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
            BillingSnapshotKeysetReader reader,
            BillingSnapShotProcessor processor,
            BillingSnapShotWriter writer,
            @Value("${app.mongo.snapshot.chunk-size:200}") int chunkSize
    ){
        var perf = new PerfTimingListener<BillingSnapshotDoc,RenderedMessage>(
                30,   // slowReadMs
                50,   // slowProcessMs
                300,  // slowWriteMs
                1000, // slowChunkMs
                doc -> doc.id() //
        );
        return new StepBuilder("billingSnapShotStep",jobRepository)
                .<BillingSnapshotDoc, RenderedMessage>chunk(chunkSize,transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener((StepExecutionListener) perf)
                .listener((ChunkListener) perf)
                .listener((ItemReadListener<BillingSnapshotDoc>) perf)
                .listener((ItemProcessListener<BillingSnapshotDoc, RenderedMessage>) perf)
                .listener((ItemWriteListener<RenderedMessage>) perf)
                .build();
    }

    @Bean
    @StepScope
    public BillingSnapshotKeysetReader billingSnapshotReader(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.mongo.snapshot.collection:billing_snapshot}") String collection,
            @Value("#{jobExecutionContext['billingYearMonth']}") String billingYear,
            @Value("${app.billing.mongo.snapshot.batch-size:200}") int pageSize
    ) {
        return new BillingSnapshotKeysetReader(mongoTemplate, collection, billingYear, pageSize);
    }

    @Bean
    public BillingSnapShotProcessor billingSnapShotProcessor (){
        return new BillingSnapShotProcessor(templateEngine);
    }

    @Bean
    @StepScope
    public BillingSnapShotWriter billingSnapShotWriter(
            @Value("${s3.bucket}") String bucket,
            S3UploadPort s3UploadPort,
            ObjectMapper om,
            BillingTargetS3UpdatePort billingTargetS3UpdatePort
    ) {
        return new BillingSnapShotWriter(bucket, s3UploadPort, om, billingTargetS3UpdatePort);
    }
}

package com.tr1l.worker.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.api.usecase.RenderBillingMessageUseCase;
import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import com.tr1l.billing.application.port.out.S3UploadPort;
import com.tr1l.util.DecryptionTool;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.util.EncryptionTool;
import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import com.tr1l.billing.application.model.RenderedMessageResult;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapShotProcessor;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapShotWriter;
import com.tr1l.worker.batch.formatjob.step.step1.BillingSnapshotKeysetReader;
import com.tr1l.worker.batch.listener.PerfTimingListener;
import com.tr1l.worker.config.step.StepLoggingListener;
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

import java.util.concurrent.Executor;

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


    public BillingSnapShotStepConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Bean
    public Step billingSnapShotStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager transactionManager,
            BillingSnapshotKeysetReader reader,
            BillingSnapShotProcessor processor,
            BillingSnapShotWriter writer,
            StepLoggingListener listener, // add
            @Value("${app.mongo.snapshot.chunk-size:200}") int chunkSize
    ){
        var perf = new PerfTimingListener<BillingSnapshotDoc,RenderedMessageResult>(
                30,   // slowReadMs
                50,   // slowProcessMs
                300,  // slowWriteMs
                1000, // slowChunkMs
                doc -> doc.id() //
        );
        return new StepBuilder("billingSnapShotStep",jobRepository)
                .<BillingSnapshotDoc, RenderedMessageResult>chunk(chunkSize,transactionManager)
                .reader(reader)
                .processor(processor)
                .listener(listener)  // add
                .writer(writer)
                .listener((StepExecutionListener) perf)
                .listener((ChunkListener) perf)
                .listener((ItemReadListener<BillingSnapshotDoc>) perf)
                .listener((ItemProcessListener<BillingSnapshotDoc, RenderedMessageResult>) perf)
                .listener((ItemWriteListener<RenderedMessageResult>) perf)
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
    @StepScope
    public BillingSnapShotProcessor billingSnapShotProcessor (
            RenderBillingMessageUseCase useCase,
            DecryptionTool decryptionTool
    ){
        return new BillingSnapShotProcessor(useCase, decryptionTool);
    }

    @Bean
    @StepScope
    public BillingSnapShotWriter billingSnapShotWriter(
            @Value("${s3.bucket}") String bucket,
            S3UploadPort s3UploadPort,
            ObjectMapper om,
            BillingTargetS3UpdatePort billingTargetS3UpdatePort,
            EncryptionTool encryptionTool,
            @Qualifier("s3UploadExecutor")Executor s3UploadExecutor
    ) {
        return new BillingSnapShotWriter(bucket,
                s3UploadPort,
                om,
                billingTargetS3UpdatePort,
                encryptionTool,
                s3UploadExecutor);
    }
}

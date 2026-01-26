package com.tr1l.worker.config.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ==========================
 * formatJobConfig
 * Job2의 step 연결 config
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */


@Configuration
public class FormatJobConfig {

    @Bean(name = "formatJob")
    public Job formatJob(
            JobRepository jobRepository,
            JobExecutionListener formatJobContextInitializer,
            Step formatGateStep,
            @Qualifier("billingSnapShotStep") Step billingSnapShotStep,
            Step formatFinalizeStep
    ) {
        return new JobBuilder("formatJob", jobRepository)
                .listener(formatJobContextInitializer)
                .start(formatGateStep)
                .on("NOOP").end()
                .from(formatGateStep)
                .on("*").to(billingSnapShotStep)
                .from(billingSnapShotStep)
                .on("*").to(formatFinalizeStep)
                .end()
                .build();
    }
}

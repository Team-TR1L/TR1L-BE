package com.tr1l.worker.config.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*==========================
 * Job1 config class
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 19.]
 * @version 1.0
 *==========================*/
@Configuration
public class CalculateJobConfig {
    /**
     * Job1: 월 정산(청구서) Job
     */
    private static final String NOOP = "NOOP";

    @Bean
    public Job calculateJob(
            JobRepository jobRepository,
            JobExecutionListener calculateJobContextInitializer,
            @Qualifier("billingGateStep") Step billingGateStep,
            @Qualifier("billingFlattenStep") Step billingFlattenStep,
            @Qualifier("billingTargetStep") Step billingTargetStep,
            @Qualifier("billingCalculateAndSnapshotPartitionedStep") Step billingCalculateAndSnapshotStep,
            @Qualifier("finalizeBillingCycleStep") Step finalizeBillingCycleStep
    ) {

        Flow flow = new FlowBuilder<Flow>("calculateJobFlow")
                .start(billingGateStep)
                    .on(NOOP).end()
                .from(billingGateStep)
                    .on("*").to(billingFlattenStep)
                    .next(billingTargetStep)
                    .next(billingCalculateAndSnapshotStep)
                    .next(finalizeBillingCycleStep)
                .end();

        return new JobBuilder("calculateJob", jobRepository)
                .listener(calculateJobContextInitializer)
                .start(flow)
                .end()
                .build();
    }

}

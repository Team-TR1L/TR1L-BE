package com.tr1l.worker.config.step;

import com.tr1l.worker.batch.calculatejob.step.step0.BillingGateTasklet;
import com.tr1l.worker.batch.listener.PerfTimingListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BillingGateStepConfig {
    private final JobRepository jobRepository;
    private final BillingGateTasklet gateTasklet;
    private final PlatformTransactionManager transactionManager;
    private final StepLoggingListener listener;

    public BillingGateStepConfig(
            JobRepository jobRepository,
            BillingGateTasklet billingGateTasklet,
            @Qualifier("TX-target") PlatformTransactionManager targetManager,
            StepLoggingListener listener
    ){
            this.gateTasklet=billingGateTasklet;
            this.jobRepository=jobRepository;
            this.transactionManager=targetManager;
            this.listener=listener;
    }

    @Bean
    public Step billingGateStep() {
        var perf = new PerfTimingListener<Object, Object>(
                30,
                50,
                300,
                1000,
                item -> "tasklet"
        );
        return new StepBuilder("billingGateStep", jobRepository)
                .tasklet(gateTasklet, transactionManager)
                .listener(listener)
                .listener((StepExecutionListener) perf)
                .build();
    }
}

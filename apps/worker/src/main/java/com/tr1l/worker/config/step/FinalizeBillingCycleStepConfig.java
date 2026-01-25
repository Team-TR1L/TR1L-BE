package com.tr1l.worker.config.step;

import com.tr1l.worker.batch.calculatejob.step.step4.FinalizeBillingCycleTasklet;
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
public class FinalizeBillingCycleStepConfig {

    @Bean(name = "finalizeBillingCycleStep")
    public Step finalizeBillingCycleStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager tx,
            FinalizeBillingCycleTasklet tasklet,
            StepLoggingListener loggingListener
    ) {
        var perf = new PerfTimingListener<Object, Object>(
                30,
                50,
                300,
                1000,
                item -> "tasklet"
        );
        return new StepBuilder("finalizeBillingCycleStep", jobRepository)
                .tasklet(tasklet, tx)
                .listener(loggingListener)
                .listener((StepExecutionListener) perf)
                .build();
    }
}

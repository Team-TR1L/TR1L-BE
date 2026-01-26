package com.tr1l.worker.config;

import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.tr1l.worker.batch.formatjob.step.step1.RetryableFormatJobException;
import com.tr1l.worker.batch.formatjob.step.step1.SkippableFormatJobException;
import org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class FormatJobFaultToleranceConfig {

    @Bean(name = "formatJobRetryPolicy")
    public RetryPolicy formatJobRetryPolicy() {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(MongoTimeoutException.class, true);
        retryable.put(MongoSocketException.class, true);
        retryable.put(TransientDataAccessException.class, true);
        retryable.put(RetryableFormatJobException.class, true);
        return new SimpleRetryPolicy(3, retryable);
    }

    @Bean(name = "formatJobSkipPolicy")
    public SkipPolicy formatJobSkipPolicy() {
        Map<Class<? extends Throwable>, Boolean> skippable = new HashMap<>();
        skippable.put(SkippableFormatJobException.class, true);
        return new LimitCheckingItemSkipPolicy(1000, skippable);
    }
}

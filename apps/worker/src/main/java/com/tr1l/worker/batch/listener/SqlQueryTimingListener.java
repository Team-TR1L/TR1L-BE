package com.tr1l.worker.batch.listener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SqlQueryTimingListener implements QueryExecutionListener {

    private static final String MDC_JOB = "batch.job";
    private static final String MDC_STEP = "batch.step";
    private final MeterRegistry meterRegistry;

    public SqlQueryTimingListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // no-op
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        String job = defaultTag(MDC.get(MDC_JOB));
        String step = defaultTag(MDC.get(MDC_STEP));
        String dataSource = defaultTag(execInfo.getDataSourceName());
        long elapsedMs = execInfo.getElapsedTime();

        Timer.builder("batch.sql.query.time")
                .tag("job", job)
                .tag("step", step)
                .tag("datasource", dataSource)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    private String defaultTag(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value;
    }
}

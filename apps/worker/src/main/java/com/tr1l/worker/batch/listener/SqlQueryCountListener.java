package com.tr1l.worker.batch.listener;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.listener.ChunkListenerSupport;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.ExecutionContext;

import java.util.Arrays;

@Slf4j
public class SqlQueryCountListener extends ChunkListenerSupport implements StepExecutionListener {

    private static final String TOTAL_PREFIX = "sql.total.";
    private final String[] dataSourceNames;
    private StepExecution stepExecution;
    private final MeterRegistry meterRegistry;

    public SqlQueryCountListener(MeterRegistry meterRegistry, String... dataSourceNames) {
        this.meterRegistry = meterRegistry;
        this.dataSourceNames = Arrays.copyOf(dataSourceNames, dataSourceNames.length);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        QueryCountHolder.clear();
        resetTotals(stepExecution.getExecutionContext());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExecutionContext ctx = stepExecution.getExecutionContext();
        for (String ds : dataSourceNames) {
            log.info("SQL_TOTAL step={} ds={} total={} select={} insert={} update={} delete={} other={}",
                    stepExecution.getStepName(),
                    ds,
                    ctx.getLong(key(ds, "total"), 0L),
                    ctx.getLong(key(ds, "select"), 0L),
                    ctx.getLong(key(ds, "insert"), 0L),
                    ctx.getLong(key(ds, "update"), 0L),
                    ctx.getLong(key(ds, "delete"), 0L),
                    ctx.getLong(key(ds, "other"), 0L)
            );
        }
        return stepExecution.getExitStatus();
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        QueryCountHolder.clear();
    }

    @Override
    public void afterChunk(ChunkContext context) {
        if (this.stepExecution == null) {
            log.warn("SQL_CHUNK skipped: stepExecution is null");
            return;
        }

        for (String ds : dataSourceNames) {
            QueryCount chunk = QueryCountHolder.get(ds);
            long total = (chunk == null) ? 0L : chunk.getTotal();
            long select = (chunk == null) ? 0L : chunk.getSelect();
            long insert = (chunk == null) ? 0L : chunk.getInsert();
            long update = (chunk == null) ? 0L : chunk.getUpdate();
            long delete = (chunk == null) ? 0L : chunk.getDelete();
            long other = (chunk == null) ? 0L : chunk.getOther();
            log.info("SQL_CHUNK step={} ds={} total={} select={} insert={} update={} delete={} other={}",
                    stepExecution.getStepName(),
                    ds,
                    total,
                    select,
                    insert,
                    update,
                    delete,
                    other
            );

            addTotals(stepExecution.getExecutionContext(), ds, total, select, insert, update, delete, other);
            recordMetrics(ds, total, select, insert, update, delete, other);
        }

        QueryCountHolder.clear();
    }

    private void resetTotals(ExecutionContext ctx) {
        for (String ds : dataSourceNames) {
            ctx.putLong(key(ds, "total"), 0L);
            ctx.putLong(key(ds, "select"), 0L);
            ctx.putLong(key(ds, "insert"), 0L);
            ctx.putLong(key(ds, "update"), 0L);
            ctx.putLong(key(ds, "delete"), 0L);
            ctx.putLong(key(ds, "other"), 0L);
        }
    }

    private void addTotals(ExecutionContext ctx, String ds, long total, long select, long insert, long update, long delete, long other) {
        ctx.putLong(key(ds, "total"), ctx.getLong(key(ds, "total"), 0L) + total);
        ctx.putLong(key(ds, "select"), ctx.getLong(key(ds, "select"), 0L) + select);
        ctx.putLong(key(ds, "insert"), ctx.getLong(key(ds, "insert"), 0L) + insert);
        ctx.putLong(key(ds, "update"), ctx.getLong(key(ds, "update"), 0L) + update);
        ctx.putLong(key(ds, "delete"), ctx.getLong(key(ds, "delete"), 0L) + delete);
        ctx.putLong(key(ds, "other"), ctx.getLong(key(ds, "other"), 0L) + other);
    }

    private void recordMetrics(String ds, long total, long select, long insert, long update, long delete, long other) {
        if (stepExecution == null) return;
        String step = stepExecution.getStepName();
        String job = stepExecution.getJobExecution().getJobInstance().getJobName();
        meterRegistry.counter("batch.sql.query.count", "job", job, "step", step, "datasource", ds, "type", "total")
                .increment(total);
        meterRegistry.counter("batch.sql.query.count", "job", job, "step", step, "datasource", ds, "type", "select")
                .increment(select);
        meterRegistry.counter("batch.sql.query.count", "job", job, "step", step, "datasource", ds, "type", "insert")
                .increment(insert);
        meterRegistry.counter("batch.sql.query.count", "job", job, "step", step, "datasource", ds, "type", "update")
                .increment(update);
        meterRegistry.counter("batch.sql.query.count", "job", job, "step", step, "datasource", ds, "type", "delete")
                .increment(delete);
        meterRegistry.counter("batch.sql.query.count", "job", job, "step", step, "datasource", ds, "type", "other")
                .increment(other);
    }

    private String key(String ds, String suffix) {
        return TOTAL_PREFIX + ds + "." + suffix;
    }
}

package com.tr1l.worker.config.step;

import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.worker.batch.calculatejob.support.BillingTargetBaseRowKeysetReader;
import com.tr1l.worker.batch.calculatejob.step.step1.BillingTargetFlattenWriter;
import com.tr1l.worker.batch.calculatejob.support.BillingTargetBaseRowMapper;
import com.tr1l.worker.batch.listener.PerfTimingListener;
import com.tr1l.worker.batch.listener.SqlQueryCountListener;
import com.tr1l.util.SqlResourceReader;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/*==========================
 * Step1: baseTargets를 읽고(Reader),
 *      chunk마다 (facts 조회 + 조립 + upsert)를 Writer에서 수행.
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 18.]
 * @version 1.0
 *==========================*/
@Configuration
public class BillingFlattenStepConfig {

    @Bean
    public BillingTargetBaseRowKeysetReader billingTargetBaseReader(
            @Qualifier("mainDataSource") DataSource mainDataSource,
            BillingTargetBaseRowMapper rowMapper,
            SqlResourceReader sqlResourceReader,
            @Value("${app.sql.step1.mainBase}/01_select_base_targets.sql") Resource baseTargetSql,
            @Value("${app.sql.step1.reader.fetchSize}") int limitSize
    ) {
        return new BillingTargetBaseRowKeysetReader(
                mainDataSource,
                rowMapper,
                sqlResourceReader.read(baseTargetSql),
                limitSize
        );
    }

    @Bean
    public Step billingFlattenStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager targetTx,
            BillingTargetBaseRowKeysetReader billingTargetBaseRowJdbcCursorItemReader,
            BillingTargetFlattenWriter writer,
            StepLoggingListener listener,
            MeterRegistry meterRegistry,
            @Value("${app.sql.step1.chunk.chunkSize}") int chunkSize
    ) {
        var perf = new PerfTimingListener<BillingTargetBaseRow, BillingTargetBaseRow>(
                30,
                50,
                300,
                1000,
                row -> Long.toString(row.userId())
        );
        var sql = new SqlQueryCountListener(meterRegistry, "main", "target");
        return new StepBuilder("billingFlattenStep", jobRepository)
                .<BillingTargetBaseRow, BillingTargetBaseRow>chunk(chunkSize, targetTx)
                .reader(billingTargetBaseRowJdbcCursorItemReader)
                .writer(writer)
                .listener(listener)
                .listener((StepExecutionListener) perf)
                .listener((ChunkListener) perf)
                .listener((ItemReadListener<BillingTargetBaseRow>) perf)
                .listener((ItemProcessListener<BillingTargetBaseRow, BillingTargetBaseRow>) perf)
                .listener((ItemWriteListener<BillingTargetBaseRow>) perf)
                .listener((StepExecutionListener) sql)
                .listener((ChunkListener) sql)
                .build();
    }
}

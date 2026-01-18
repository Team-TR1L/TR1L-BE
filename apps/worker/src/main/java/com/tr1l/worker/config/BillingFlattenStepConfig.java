package com.tr1l.worker.config;

import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.worker.batch.calculatejob.step.step1.BillingTargetFlattenWriter;
import com.tr1l.worker.batch.calculatejob.support.BillingTargetBaseRowMapper;
import com.tr1l.util.SqlResourceReader;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
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
    @Value("${app.sql.step1.reader.fetchSize}")
    private int chunkSize;

    @Bean
    public JdbcCursorItemReader<BillingTargetBaseRow> billingTargetBaseReader(
            @Qualifier("mainDataSource") DataSource mainDataSource,
            BillingTargetBaseRowMapper rowMapper,
            SqlResourceReader sqlResourceReader,
            @Value("${app.sql.step1.mainBase}/01_select_base_targets.sql") Resource baseTargetSql,
            @Value("${app.sql.step1.reader.fetchSize}") int fetchSize
    ) {
        return new JdbcCursorItemReaderBuilder<BillingTargetBaseRow>()
                .name("billingTargetBaseReader")
                .dataSource(mainDataSource)
                .sql(sqlResourceReader.read(baseTargetSql))
                .rowMapper(rowMapper)
                .fetchSize(fetchSize)
                .build();
    }

    @Bean
    public Step billingFlattenStep(
            JobRepository jobRepository,
            @Qualifier("TX-target") PlatformTransactionManager targetTx,
            JdbcCursorItemReader<BillingTargetBaseRow> billingTargetBaseRowJdbcCursorItemReader,
            BillingTargetFlattenWriter writer
    ) {
        return new StepBuilder("billingFlattenStep",jobRepository)
                .<BillingTargetBaseRow, BillingTargetBaseRow>chunk(chunkSize, targetTx)
                .reader(billingTargetBaseRowJdbcCursorItemReader)
                .writer(writer)
                .build();
    }
}

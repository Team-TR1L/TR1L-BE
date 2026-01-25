package com.tr1l.worker.batch.calculatejob.support;

import com.tr1l.billing.application.model.BillingTargetBaseRow;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
/*==========================
 * Keyset Paging 기반의 Spring Batch ItemStreamReader
 *
 * 대량 데이터 조회 시 OFFSET 기반 페이징
 * "WHERE user_id > lastUserId ORDER BY user_id LIMIT pageSize" 형태로 스트리밍
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 25.]
 * @version 1.0
 *==========================*/
public class BillingTargetBaseRowKeysetReader implements ItemStreamReader<BillingTargetBaseRow> {

    //청크 커밋 시점
    private static final String EC_LAST_USER_ID = "step1.lastUserId";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<BillingTargetBaseRow> rowMapper;
    private final String sql;
    private final int limitSize;

    private long lastUserId = 0L;
    private Iterator<BillingTargetBaseRow> buffer = Collections.emptyIterator();

    public BillingTargetBaseRowKeysetReader(
            DataSource dataSource,
            RowMapper<BillingTargetBaseRow> rowMapper,
            String sql,
            int limitSize
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.rowMapper = rowMapper;
        this.sql = sql;
        this.limitSize = limitSize;
    }

    /**
     * Step 시작 시 호출
     * @param executionContext current step's
     * {@link org.springframework.batch.item.ExecutionContext}. Will be the
     * executionContext from the last run of the step on a restart.
     * @throws ItemStreamException
     */
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (executionContext.containsKey(EC_LAST_USER_ID)) {
            this.lastUserId = executionContext.getLong(EC_LAST_USER_ID);
        }
    }

    /**
     * Item 반환
     * @return
     */
    @Override
    public BillingTargetBaseRow read() {
        if (!buffer.hasNext()) {
            List<BillingTargetBaseRow> page = jdbcTemplate.query(
                    con -> {
                        PreparedStatement ps = con.prepareStatement(sql);
                        ps.setLong(1, lastUserId);
                        ps.setInt(2, limitSize);
                        return ps;
                    },
                    rowMapper
            );

            if (page.isEmpty()) return null;
            buffer = page.iterator();
        }

        BillingTargetBaseRow item = buffer.next();
        lastUserId = item.userId(); //다음 페이지
        return item;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        //다음 컨텍스트로
        executionContext.putLong(EC_LAST_USER_ID, lastUserId);
    }

    @Override
    public void close() {
        buffer = Collections.emptyIterator();
    }
}

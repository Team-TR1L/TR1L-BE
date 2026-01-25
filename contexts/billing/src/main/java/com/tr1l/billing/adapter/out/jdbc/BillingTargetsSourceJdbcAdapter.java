package com.tr1l.billing.adapter.out.jdbc;

import com.tr1l.billing.application.model.BillingTargetFacts;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.billing.application.port.out.BillingTargetSourcePort;
import com.tr1l.billing.application.model.ContractFact;
import com.tr1l.billing.application.model.OptionItemRow;
import com.tr1l.util.SqlResourceReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Repository
public class BillingTargetsSourceJdbcAdapter implements BillingTargetSourcePort {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SqlResourceReader resourceReader;
    private final TransactionTemplate mainReadTx; //Main DB READ 컨트롤 Tx
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.sql.step1.reader.bulkSize}")
    private int bulkSize;

    public BillingTargetsSourceJdbcAdapter(
            @Qualifier("mainNamedJdbcTemplate") NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            @Qualifier("mainDataSource") DataSource dataSource,
            @Qualifier("TX-main") PlatformTransactionManager transactionManager,
            SqlResourceReader resourceReader
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.resourceReader = resourceReader;
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        this.mainReadTx = new TransactionTemplate(transactionManager);

        //main DB READ + temp table 1개의 커넥션으로 고정
        this.mainReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    }

    //SQL 파일 주입
    @Value("${app.sql.step1.mainBase}/02_select_usage.sql")
    private Resource usageSql;
    @Value("${app.sql.step1.mainBase}/03_select_active_contract.sql")
    private Resource contractSql;
    @Value("${app.sql.step1.mainBase}/04_select_active_soldiers.sql")
    private Resource soldiersSql;
    @Value("${app.sql.step1.mainBase}/05_select_included_options.sql")
    private Resource optionsSql;

    @Override
    public BillingTargetFacts fetchFacts(List<Long> userIds, BillingTargetFlatParams params) {
        //temp table 생성/적재 -> 쿼리 같은 커넥션
        return mainReadTx.execute(status -> {
            //temp table setup
            jdbcTemplate.execute("""
                    CREATE TEMP TABLE IF NOT EXISTS temp_user_ids (
                        user_id bigint PRIMARY KEY
                    ) ON COMMIT PRESERVE ROWS;
                    """);

            //ROLLBACK 이후 커넥션 재서용 시 항상 비우고 시작
            jdbcTemplate.execute("TRUNCATE TABLE temp_user_ids");

            jdbcTemplate.batchUpdate(
                    "INSERT INTO temp_user_ids(user_id) VALUES (?) ON CONFLICT DO NOTHING ",
                    userIds,
                    bulkSize,
                    (ps, id) -> ps.setLong(1, id)
            );

            //아래 쿼리는 동일 커넥션에서 temp table read
            Map<Long, Long> usage = fetchUsage(params.yearMonth());
            Map<Long, ContractFact> contracts = fetchContracts(params.startDate(), params.endDate());
            Set<Long> soldiers = fetchSoldiers(params.startDate());
            List<OptionItemRow> options = fetchSubscriptionOptionItems(params.startDate(), params.endDate());
            return new BillingTargetFacts(usage, contracts, soldiers, options);
        });
    }

    /*==========================
    *
    *BillingTargetsSourceJdbcAdapter
    *
    * 유저의 아이디별 정산월의 데이터 총 사용량 조회
    *
    * @parm
    *   - userIds: 유효한 사용자 아이디
    *   - yearMonth: 2022-12 정산 기준 연월
    * @return Map<유저 아이디, 유저의 데이터 사용량>  
    * @author kimdoyeon
    * @version 1.0.0
    * @date 26. 1. 18.
    *
    ==========================**/
    private Map<Long, Long> fetchUsage(String yearMonth) {
        String sql = resourceReader.read(usageSql);

        var params = new MapSqlParameterSource()
                .addValue("yearMonth", yearMonth);

        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            Map<Long, Long> out = new HashMap<>();

            while (rs.next()) {
                out.put(rs.getLong("user_id"),
                        rs.getLong("used_data_mb"));
            }
            return out;
        });
    }

    /*==========================
    *
    *BillingTargetsSourceJdbcAdapter
    *
    * 유저의 정산 월 기준 선택 약정 조회
    *
    * @parm
    *   - userIds: 유효한 유저 아이디
    *   - startDate: 정산 시작일
    *   - endDate: 정산 마감일
    * @return Map<유저아이디, 선택 약정 정보>
    * @author kimdoyeon
    * @version 1.0.0
    * @date 26. 1. 18.
    *
    ==========================**/
    private Map<Long, ContractFact> fetchContracts(LocalDate startDate, LocalDate endDate) {
        String sql = resourceReader.read(contractSql);
        var params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            Map<Long, ContractFact> out = new HashMap<>();

            while (rs.next()) {
                long userId = rs.getLong("user_id");
                int months = rs.getInt("duration_months");
                BigDecimal rate = rs.getBigDecimal("discount_percent");

                out.put(userId, new ContractFact(months, rate));
            }

            return out;
        });
    }

    /*==========================
    *
    *BillingTargetsSourceJdbcAdapter
    *
    * 유저의 군인 여부 조회 및 유효성 검증 후 유효한 유저 아이디 반환
    *
    * @parm
    *   - userIds: 유효한 유저 아이디
    *   - startDate : 정산 시작 기준일
    * @return Set<유저 아이디>
    * @author kimdoyeon
    * @version 1.0.0
    * @date 26. 1. 18.
    *
    ==========================**/
    private Set<Long> fetchSoldiers(LocalDate startDate) {
        String sql = resourceReader.read(soldiersSql);

        var params = new MapSqlParameterSource()
                .addValue("startDate", startDate);

        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            Set<Long> out = new HashSet<>();

            while (rs.next()) {
                long userId = rs.getLong("user_id");

                out.add(userId);
            }

            return out;
        });
    }

    /*==========================
    *
    *BillingTargetsSourceJdbcAdapter
    *
    * 유저의 구독 서비스 조회
    *
    * @parm
    *   - userIds: 유효한 유저 아이디
    *   - startDate: 정산 시작일
    *   - endDate: 정산 마감일
    * @return
    * @author kimdoyeon
    * @version 1.0.0
    * @date 26. 1. 18.
    *
    ==========================**/
    private List<OptionItemRow> fetchSubscriptionOptionItems(LocalDate startDate, LocalDate endDate) {
        String sql = resourceReader.read(optionsSql);

        var params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> new OptionItemRow(
                rs.getLong("user_id"),
                rs.getString("option_service_code"),
                rs.getString("option_service_name"),
                rs.getLong("monthly_price")
        ));
    }
}

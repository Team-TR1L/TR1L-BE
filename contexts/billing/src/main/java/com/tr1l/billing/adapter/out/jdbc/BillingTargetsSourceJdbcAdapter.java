package com.tr1l.billing.adapter.out.jdbc;

import com.tr1l.billing.application.model.BillingTargetFacts;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.billing.application.port.out.BillingTargetSourcePort;
import com.tr1l.billing.application.model.ContractFact;
import com.tr1l.billing.application.model.OptionItemRow;
import com.tr1l.util.SqlResourceReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Repository
public class BillingTargetsSourceJdbcAdapter implements BillingTargetSourcePort {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SqlResourceReader resourceReader;

    public BillingTargetsSourceJdbcAdapter(
            @Qualifier("mainNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            SqlResourceReader resourceReader
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.resourceReader = resourceReader;
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
        Map<Long, Long> usage = fetchUsage(userIds, params.yearMonth());
        Map<Long, ContractFact> contracts = fetchContracts(userIds, params.startDate(),params.endDate());
        Set<Long> soldiers = fetchSoldiers(userIds, params.startDate());
        List<OptionItemRow> options = fetchSubscriptionOptionItems(userIds, params.startDate(),params.endDate());

        return new BillingTargetFacts(usage, contracts, soldiers, options);
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
    private Map<Long, Long> fetchUsage(List<Long> userIds, String yearMonth) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        String sql = resourceReader.read(usageSql);

        //파라미터
        var params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("yearMonth", yearMonth);


        return jdbcTemplate.query(sql, params, rs -> {
            Map<Long, Long> out = new HashMap<>();

            while (rs.next()) {
                out.put(
                        rs.getLong("user_id"),
                        rs.getLong("used_data_mb")
                );
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
    private Map<Long, ContractFact> fetchContracts(List<Long> userIds, LocalDate startDate, LocalDate endDate) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        String sql = resourceReader.read(contractSql);
        //파라미터 셋업
        var params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbcTemplate.query(sql, params, rs -> {
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
    private Set<Long> fetchSoldiers(List<Long> userIds, LocalDate startDate) {
        if (userIds == null || userIds.isEmpty()) return Set.of();

        //파라미터 셋업
        var params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("startDate", startDate);

        String sql = resourceReader.read(soldiersSql);

        return jdbcTemplate.query(sql, params, rs -> {
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
    private List<OptionItemRow> fetchSubscriptionOptionItems(List<Long> userIds, LocalDate startDate, LocalDate endDate) {
        if (userIds == null || userIds.isEmpty()) return List.of();

        String sql = resourceReader.read(optionsSql);

        //파라미터 셋업
        var params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new OptionItemRow(
                rs.getLong("user_id"),
                rs.getString("option_service_code"),
                rs.getString("option_service_name"),
                rs.getLong("monthly_price")
        ));
    }
}

package com.tr1l.worker.batch.calculatejob.step.step2;

import com.tr1l.worker.batch.calculatejob.model.BillingTargetKey;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;


public class BillingTargetReader extends JdbcPagingItemReader<BillingTargetKey> {

    public BillingTargetReader(DataSource dataSource, String viewName, String billingMonth, int pageSize) throws Exception{

        /**x
         * 쿼리를 이용해 Step1 에서 생성된 물리적 테이블 뷰에서 사용자 id 값과 billingMonth를 갖고 옵니다.
         */
        SqlPagingQueryProviderFactoryBean queryProviderFactory = new SqlPagingQueryProviderFactoryBean();
        queryProviderFactory.setDataSource(dataSource);
        queryProviderFactory.setSelectClause("SELECT billing_month, user_id");
        queryProviderFactory.setFromClause("FROM " + viewName);
        queryProviderFactory.setWhereClause("WHERE billing_month = :billingMonth");

        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("user_id",Order.ASCENDING);
        queryProviderFactory.setSortKeys(sortKeys);

        /**
         * 결과를 매핑해서 processor 에게 넘겨준다.
         */
        LocalDate billingMonthDay = parseToBillingMonthDay(billingMonth);
        PagingQueryProvider queryProvider = queryProviderFactory.getObject();
        setName("step2Reader");
        setDataSource(dataSource);
        setQueryProvider(queryProvider);
        setParameterValues(Map.of("billingMonth",billingMonthDay));
        setSaveState(false); //restart 지점 보장 X
        setPageSize(pageSize);
        setRowMapper((rs,rowNum)->
                new BillingTargetKey(rs.getDate("billing_month").toLocalDate(), rs.getLong("user_id")));

        afterPropertiesSet();

    }

    private static LocalDate parseToBillingMonthDay(String billingMonthParam) {
        // 1) "YYYY-MM"이면 YearMonth로
        if (billingMonthParam.length() == 7) {
            return YearMonth.parse(billingMonthParam).atDay(1);
        }
        // 2) "YYYY-MM-DD"이면 LocalDate로 받고 "1일"로 강제 정규화
        if (billingMonthParam.length() == 10) {
            LocalDate d = LocalDate.parse(billingMonthParam);
            return YearMonth.from(d).atDay(1);
        }
        throw new IllegalArgumentException("billingMonth format must be YYYY-MM or YYYY-MM-DD: " + billingMonthParam);
    }


}

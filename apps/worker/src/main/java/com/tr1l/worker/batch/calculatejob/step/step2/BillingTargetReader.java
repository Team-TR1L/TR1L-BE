package com.tr1l.worker.batch.calculatejob.step.step2;

import com.tr1l.worker.batch.calculatejob.model.Step2BillingTargetKey;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;


public class Step2Reader extends JdbcPagingItemReader<Step2BillingTargetKey> {

    public Step2Reader(DataSource dataSource, String viewName, String billingMonth, int pageSize) throws Exception{

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

        PagingQueryProvider queryProvider = queryProviderFactory.getObject();
        setName("step2Reader");
        setDataSource(dataSource);
        setQueryProvider(queryProvider);
        setParameterValues(Map.of("billingMonth",billingMonth));
        setPageSize(pageSize);
        setRowMapper((rs,rowNum)->
                new Step2BillingTargetKey(rs.getString("billing_month"), rs.getLong("user_id")));

        afterPropertiesSet();

    }


}

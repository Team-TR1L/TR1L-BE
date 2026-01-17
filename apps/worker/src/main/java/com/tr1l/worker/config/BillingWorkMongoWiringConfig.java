package com.tr1l.worker.config;


import com.tr1l.billing.adapter.out.persistence.MongoWorkDocUpsertAdapter;
import com.tr1l.billing.application.port.out.WorkDocUpsertPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;


/**
 * step 2 에서 디비 연결 config
 **/
@Configuration
public class BillingWorkMongoWiringConfig {
    @Bean
    public WorkDocUpsertPort workDocUpsertPort(
            MongoTemplate mongoTemplate,
            @Value("${app.billing.step2.work-collection:billing_work}") String collectionName
    ) {
        return new MongoWorkDocUpsertAdapter(mongoTemplate, collectionName);
    }

}

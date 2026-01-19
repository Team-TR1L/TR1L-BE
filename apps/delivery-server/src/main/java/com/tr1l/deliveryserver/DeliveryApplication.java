package com.tr1l.deliveryserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.tr1l.deliveryserver", // 내 서버 어플리케이션
        "com.tr1l.delivery"        // 내가 쓰는 도메인/어댑터 로직
})
@EnableJpaRepositories(basePackages = "com.tr1l.dispatch.infra.persistence.repository")
@EntityScan(basePackages = "com.tr1l.dispatch.infra.persistence.entity")
public class DeliveryApplication {
     public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

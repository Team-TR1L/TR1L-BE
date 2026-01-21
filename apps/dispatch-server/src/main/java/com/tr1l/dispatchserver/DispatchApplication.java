package com.tr1l.dispatchserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.tr1l.dispatchserver", // 실행 서버 패키지
        "com.tr1l.dispatch"        // 비즈니스 로직(Context) 패키지
})
@EnableJpaRepositories(basePackages = "com.tr1l.dispatch.infra.persistence.repository")
@EntityScan(basePackages = "com.tr1l.dispatch.infra.persistence.entity")
public class DispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchApplication.class, args);
    }
}
package com.tr1l.delivery.infra.config;

import com.tr1l.util.DecryptionTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeliveryConfig {
    @Value("${secret-key}") // delivery 전용 키
    private String deliveryKey;
    @Bean
    public DecryptionTool deliveryDecrypter() {
        // 배달용 키를 꽂아서 생성!
        return new DecryptionTool(deliveryKey);
    }
}

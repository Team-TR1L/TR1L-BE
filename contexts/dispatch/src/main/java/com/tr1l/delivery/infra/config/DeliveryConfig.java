package com.tr1l.delivery.infra.config;

import com.tr1l.util.DecryptionTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeliveryConfig {
    @Bean
    public DecryptionTool deliveryDecrypt(@Value("${secret-key}") String deliveryKey) {
        return new DecryptionTool(deliveryKey);
    }
}

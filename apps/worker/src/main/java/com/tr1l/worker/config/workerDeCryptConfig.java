package com.tr1l.worker.config;

/**
 * ==========================
 * workerDeCryptConfig
 * Job2의 step1
 * 복호화 클래스 컨피그 파일
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import com.tr1l.util.DecryptionTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class workerDeCryptConfig {


    @Bean
    public DecryptionTool deliveryDecrypt(@Value("${secret-key}") String deliveryKey,
                                          @Value("${algorithm:AES}")String algorithm,
                                          @Value("${transformation:AES/GCM/NoPadding}")String transformation) {
        return new DecryptionTool(deliveryKey, algorithm, transformation);
    }
}



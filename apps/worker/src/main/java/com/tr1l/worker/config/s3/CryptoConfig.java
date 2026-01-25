package com.tr1l.worker.config.s3;

import com.tr1l.util.DecryptionTool;
import com.tr1l.util.EncryptionTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Bean
    public EncryptionTool encryptionTool(
            @Value("${crypto.secret-key}") String secretKey,
            @Value("${crypto.algorithm}") String algorithm,
            @Value("${crypto.transformation}") String transformation
    ) {
        validateKeyLength(secretKey);
        return new EncryptionTool(secretKey, algorithm, transformation);
    }

    @Bean
    public DecryptionTool decryptionTool(
            @Value("${crypto.secret-key}") String secretKey,
            @Value("${crypto.algorithm}") String algorithm,
            @Value("${crypto.transformation}") String transformation
    ) {
        validateKeyLength(secretKey);
        return new DecryptionTool(secretKey, algorithm, transformation);
    }

    private void validateKeyLength(String secretKey) {
        int len = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (len != 16 && len != 24 && len != 32) {
            throw new IllegalStateException("AES key must be 16/24/32 bytes. current=" + len);
        }
    }
}

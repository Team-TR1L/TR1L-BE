package com.tr1l.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * AES-256 암호화
 * - encrypt() 결과는 Base64 문자열
 * - DecryptionTool.decrypt()와 호환
 */
public class EncryptionTool {
    private final String secretKey;
    private final String ALGORITHM;
    private final String TRANSFORMATION;

    public EncryptionTool(String secretKey, String ALGORITHM, String TRANSFORMATION) {
        this.secretKey = secretKey;
        this.ALGORITHM = ALGORITHM;
        this.TRANSFORMATION = TRANSFORMATION;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;

        try {
            // 키 생성
            SecretKeySpec keySpec =
                    new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);

            // 암호화
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encryptedBytes =
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Base64 인코딩(문자열로 저장/전송하기 위함)
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
}

package com.tr1l.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * AES-256 복호화
 */
public class DecryptionTool {
    private final String secretKey;
    private final String ALGORITHM;
    private final String TRANSFORMATION;

    public DecryptionTool(String secretKey, String ALGORITHM, String TRANSFORMATION) {
        this.secretKey = secretKey;
        this.ALGORITHM = ALGORITHM;
        this.TRANSFORMATION = TRANSFORMATION;
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) return null;

        try {
            // Base64 디코딩 : 암호문은 보통 Base64 문자열로 옴
            byte[] decodedBytes = Base64.getDecoder().decode(cipherText);

            // 키 생성
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);

            // 복호화
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            // 문자열 반환
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
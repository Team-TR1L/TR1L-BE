package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.DecryptionPort;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * AES-256 복호화
 */
@Component
public class DecryptionAdapter implements DecryptionPort {

    // 시크릿 키 주입 필요
    @Value("${secret-key}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Override
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
            throw new DispatchDomainException(DispatchErrorCode.DECRYPTION_FAILED);
        }
    }
}
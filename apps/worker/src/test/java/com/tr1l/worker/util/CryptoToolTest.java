package com.tr1l.worker.util;


import com.tr1l.util.DecryptionTool;
import com.tr1l.util.EncryptionTool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CryptoToolTest {

    // 32 bytes (AES-256)
    private static final String SECRET_KEY = "0123456789abcdef0123456789abcdef";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Test
    void encrypt_then_decrypt_roundtrip() {
        EncryptionTool enc = new EncryptionTool(SECRET_KEY, ALGORITHM, TRANSFORMATION);
        DecryptionTool dec = new DecryptionTool(SECRET_KEY, ALGORITHM, TRANSFORMATION);

        String plain = "maru-test / 2026-01/123/EMAIL.gz";
        String cipher = enc.encrypt(plain);
        log.debug("cipher={}", cipher);

        assertNotNull(cipher);
        assertNotEquals(plain, cipher);

        String restored = dec.decrypt(cipher);
        assertEquals(plain, restored);
    }

    @Test
    void encrypt_blank_returnsNull() {
        EncryptionTool enc = new EncryptionTool(SECRET_KEY, ALGORITHM, TRANSFORMATION);

        assertNull(enc.encrypt(null));
        assertNull(enc.encrypt(""));
        assertNull(enc.encrypt("   "));
    }
}

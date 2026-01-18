package com.tr1l.delivery.application.port.out;

public interface DecryptionPort {
    /*
     * 암호화된 데이터를 평문으로 복호화
     */
    String decrypt(String cipherText);
}
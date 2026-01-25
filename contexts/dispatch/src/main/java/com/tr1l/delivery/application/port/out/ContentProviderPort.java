package com.tr1l.delivery.application.port.out;

public interface ContentProviderPort {
    /*
     * URL에 있는 리소스를 다운로드하여 문자열로 반환
     */
    String downloadContent(String bucketName, String key);
}
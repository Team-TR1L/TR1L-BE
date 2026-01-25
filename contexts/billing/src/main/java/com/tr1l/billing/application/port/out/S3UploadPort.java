package com.tr1l.billing.application.port.out;

public interface S3UploadPort {

    // 원본 업로드
    S3PutResult putBytes(String bucket, String key, byte[] body, String contentType);

    // 01.23 개선 -> Gzip으로 압축 -> 선 구현 후 zip, 멀티파트 추가 구현으로 성능 비교 예정
    S3PutResult putGzipBytes(String bucket, String key, byte[] body, String contentType);

;
    //  s3에 업로드
    record S3PutResult(String bucket, String key) {}



}

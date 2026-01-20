package com.tr1l.billing.application.port.out;

public interface S3UploadPort {

    S3PutResult putBytes(String bucket, String key, byte[] body, String contentType);

    //  s3에 업로드
    record S3PutResult(String bucket, String key) {}
}

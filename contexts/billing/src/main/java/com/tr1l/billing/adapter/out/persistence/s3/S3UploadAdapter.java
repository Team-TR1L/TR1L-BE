package com.tr1l.billing.adapter.out.persistence.s3;

import com.tr1l.billing.application.port.out.S3UploadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3UploadAdapter  implements S3UploadPort {

    private final S3Client s3Client;

    @Override
    public S3PutResult putBytes(String bucket, String key, byte[] body, String contentType) {
        log.warn("[s3 start]");
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket) // 버킷 이름
                        .key(key) // billingMonth + userId + Type
                        .contentType(contentType) // "text/html; charset=utf-8"
                        .build(),
                RequestBody.fromBytes(body) // 실제 청구서
        );
        log.warn("[s3 end]");

        return new S3PutResult(bucket, key);
    }
}

package com.tr1l.billing.adapter.out.persistence.s3;

import com.tr1l.billing.application.port.out.S3UploadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3UploadAdapter  implements S3UploadPort {

    private final S3Client s3Client;
    private final S3AsyncClient s3AsyncClient;

    @Override
    public S3PutResult putBytes(String bucket, String key, byte[] body, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket) // 버킷 이름
                        .key(key) // billingMonth + userId + Type
                        .contentType(contentType) // "text/html; charset=utf-8"
                        .build(),
                RequestBody.fromBytes(body) // 실제 청구서
        );

        return new S3PutResult(bucket, key);
    }

    @Override
    public S3PutResult putGzipBytes(String bucket, String key, byte[] body, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentEncoding("gzip")
                        .build(),
                RequestBody.fromBytes(body)
        );

        return new S3PutResult(bucket, key);


    }


    /**
     * 비동기로 구현
     */
    @Override
    public CompletableFuture<S3PutResult> putBytesAsync(String bucket, String key, byte[] bytes, String contentType) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();

        return s3AsyncClient.putObject(req, AsyncRequestBody.fromBytes(bytes))
                .thenApply(resp -> new S3PutResult(bucket, key));
    }

    /**
     * 비동기로 구현
     */
    @Override
    public CompletableFuture<S3PutResult> putGzipBytesAsync(String bucket, String key, byte[] gzBytes, String contentType) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentEncoding("gzip")
                .contentLength((long) gzBytes.length)
                .build();

        return s3AsyncClient.putObject(req, AsyncRequestBody.fromBytes(gzBytes))
                .thenApply(resp -> new S3PutResult(bucket, key));
    }
}

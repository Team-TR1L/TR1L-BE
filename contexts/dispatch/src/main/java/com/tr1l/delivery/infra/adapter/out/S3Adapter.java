package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.ContentProviderPort;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3Adapter implements ContentProviderPort {

    // Spring Cloud AWS가 자동으로 빈으로 등록해줌
    private final S3Client s3Client;

    @Override
    public String downloadContent(String bucketName, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request)) {

                InputStream targetStream = s3Stream;

                // 확장자 기반: .gz 인 경우에만 gzip 해제
                if (key != null && key.endsWith(".gz")) {
                    targetStream = new GZIPInputStream(s3Stream);
                }

                return StreamUtils.copyToString(targetStream, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("S3 다운로드 실패, Error: {}", e.getMessage(), e);
            throw new DispatchDomainException(DispatchErrorCode.S3_DOWNLOAD_FAILED);
        }
    }
}
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

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3Adapter implements ContentProviderPort {

    // Spring Cloud AWS가 자동으로 빈으로 등록해줌
    private final S3Client s3Client;

    @Override
    public String downloadContent(String s3Url) {
        try {
            // URL 파싱
            URI uri = URI.create(s3Url);
            String host = uri.getHost(); // 예: my-bucket.s3.ap-northeast-2.amazonaws.com

            // 버킷 이름 추출: 첫 번째 점(.) 앞부분만 가져옴
            String bucketName = host.split("\\.")[0];

            // Key 추출: 앞의 / 제거
            String key = uri.getPath().substring(1);

            // S3 요청 객체 생성
            // AWS SDK에게 "이 버킷에 있는, 이 키(파일)를 주세요"라고 요청을 만듦
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            // ResponseInputStream은 S3에서 데이터를 조금씩 흘려보내주는 통로
            try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request)) {

                // 문자열로 변환하여 반환
                return StreamUtils.copyToString(s3Stream, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("S3 다운로드 실패. URL: {}, Error: {}", s3Url, e.getMessage(), e);
            throw new DispatchDomainException(DispatchErrorCode.S3_DOWNLOAD_FAILED);
        }
    }
}
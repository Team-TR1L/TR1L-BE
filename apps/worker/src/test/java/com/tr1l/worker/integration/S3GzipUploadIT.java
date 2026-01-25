package com.tr1l.worker.integration;

import com.tr1l.billing.adapter.out.persistence.s3.S3UploadAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@TestPropertySource(properties = {
        "AWS_ACCESS_KEY_ID="
})
class S3GzipUploadIT {

    @Test
    void putGzipBytes_uploads_smoke() {
        String region = "ap-northeast-2";
        String bucket = "maru-test01";

        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build()) {

            S3UploadAdapter adapter = new S3UploadAdapter(s3Client);

            String key = "test/" + UUID.randomUUID() + ".gz";


            String html = """
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Ping</title>
</head>
<body>
  <h1>ping</h1>
</body>
</html>
""";

            byte[] anyBytes = html.getBytes(StandardCharsets.UTF_8);

                adapter.putGzipBytes(bucket, key, anyBytes, "text/html; charset=utf-8");
            }
        }
    }

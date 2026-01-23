package com.tr1l.worker.batch.formatjob.step.step1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import com.tr1l.billing.application.port.out.S3UploadPort;
import com.tr1l.worker.batch.formatjob.domain.RenderedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Slf4j
@StepScope
public class BillingSnapShotWriter implements ItemWriter<RenderedMessage> {

    private final String bucket;
    private final S3UploadPort s3UploadPort;
    private final BillingTargetS3UpdatePort billingTargetS3UpdatePort;
    private final ObjectMapper om;


    public BillingSnapShotWriter(String bucket,
                                 S3UploadPort s3UploadPort,
                                 ObjectMapper om,
                                 BillingTargetS3UpdatePort billingTargetS3UpdatePort) {
        this.bucket = bucket;
        this.s3UploadPort = s3UploadPort;
        this.om = om;
        this.billingTargetS3UpdatePort = billingTargetS3UpdatePort;
    }

    @Override
    public void write(Chunk<? extends RenderedMessage> chunk) throws Exception {

        // for문에서 update 객체를 누적하여 bulk 연산으로 일괄 처리
        List<BillingTargetS3UpdatePort.UpdateRequest> updates = new ArrayList<>(chunk.size());


        for (RenderedMessage msg : chunk) {
            YearMonth billingYm = resolveBillingYearMonth(msg);

            // key -> YYYY-MM + userId + type
            String base = msg.period() + "/" + msg.userId() + "/";
            String emailKey = base + "EMAIL.html.gz"; // 2025-12/122/EMAIL.html
            String smsKey = base + "SMS.txt.gz"; // 2025-12/122/SMS.txt
            log.warn("base = {} , {} , {}", base, emailKey, smsKey);

            ArrayNode s3Array = om.createArrayNode(); // [] jsonb 형식


            // 1) EMAIL 업로드
            if (hasText(msg.emailHtml())) {
                byte[] raw = msg.emailHtml().getBytes(StandardCharsets.UTF_8);
                byte[] gz  = gzip(raw);

                S3UploadPort.S3PutResult put = s3UploadPort.putGzipBytes(
                        bucket,
                        emailKey,
                        gz,
                        "text/html; charset=utf-8"
                );
                log.warn("S3Put Bucket = {} , Key = {}", put.bucket(), put.key());
                s3Array.add(s3UrlItem("EMAIL", put.bucket(), put.key()));
            }

            // 2) SMS 업로드
            if (hasText(msg.smsText())) {
                byte[] raw = msg.smsText().getBytes(StandardCharsets.UTF_8);
                byte[] gz  = gzip(raw); // 압축된 바이트

                S3UploadPort.S3PutResult put = s3UploadPort.putGzipBytes(
                        bucket,
                        smsKey,
                        gz,
                        "text/plain; charset=utf-8"
                );
                log.warn("S3Put Bucket = {} , Key = {}", put.bucket(), put.key());

                s3Array.add(s3UrlItem("SMS", put.bucket(), put.key()));
            }

            if (s3Array.isEmpty()) {
                throw new IllegalStateException("RenderedMessage has no contents. userId=" + msg.userId());
            }

            // updates 요청 객체를 저장 chunk개수만큼 적재 후 일괄 처리
            updates.add(new BillingTargetS3UpdatePort.UpdateRequest(
                    billingYm,
                    msg.userId(),
                    om.writeValueAsString(s3Array)
            ));
        }

        // chunked 1회 반영
        billingTargetS3UpdatePort.updateStatusBulk(updates);
    }


    /**
     * jsonb 형식으로 변환
     */
    private ObjectNode s3UrlItem(String channelKey, String bucket, String s3Key) {
        ObjectNode n = om.createObjectNode();
        n.put("key", channelKey);
        n.put("bucket", bucket);
        n.put("s3_key", s3Key);
        log.warn("ObjectNode = {}", n);
        return n;
    }


    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }


    // String -> YearMonth로 변환
    private YearMonth resolveBillingYearMonth(RenderedMessage msg) {
        if (hasText(msg.period())) return YearMonth.parse(msg.period());
        throw new IllegalArgumentException("RenderedMessage period/billingMonth missing. userId=" + msg.userId());
    }


    // gzip 변환
    private static byte[] gzip(byte[] raw) {
        if (raw == null || raw.length == 0) return new byte[0];

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(baos)) {

            gos.write(raw);
            gos.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("gzip compression failed", e);
        }
    }
}

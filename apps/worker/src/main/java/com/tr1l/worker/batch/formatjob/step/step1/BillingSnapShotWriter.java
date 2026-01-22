package com.tr1l.worker.batch.formatjob.step.step1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import com.tr1l.billing.application.port.out.S3UploadPort;
import com.tr1l.billing.application.model.RenderedMessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

@Slf4j
@StepScope
public class BillingSnapShotWriter implements ItemWriter<RenderedMessageResult> {

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
    public void write(Chunk<? extends RenderedMessageResult> chunk) throws Exception {
        for (RenderedMessageResult msg : chunk) {

            log.warn("resolveBillingYearMonth start");
            // 어댑터에서 Date로 변환
            YearMonth billingYm = resolveBillingYearMonth(msg);
            log.warn("resolveBillingYearMonth end");

            // key -> YYYY-MM + userId + type
            String base = msg.period() + "/" + msg.userId() + "/";
            String emailKey = base + "EMAIL.html";
            String smsKey   = base + "SMS.txt";
            log.warn("base = {} , {} , {}",base,emailKey,smsKey);

            ArrayNode s3Array = om.createArrayNode(); // [] jsonb 형식
            log.warn("s3Array = {} ",s3Array.toString());

            // 1) EMAIL 업로드
            if (hasText(msg.emailHtml())) {

                S3UploadPort.S3PutResult put = s3UploadPort.putBytes(
                        bucket,
                        emailKey,
                        msg.emailHtml().getBytes(StandardCharsets.UTF_8),
                        "text/html; charset=utf-8"
                );
                log.warn("S3Put Bucket = {} , Key = {}", put.bucket(),put.key());
                s3Array.add(s3UrlItem("EMAIL", put.bucket(), put.key()));
            }

            // 2) SMS 업로드
            if (hasText(msg.smsText())) {
                S3UploadPort.S3PutResult put = s3UploadPort.putBytes(
                        bucket,
                        smsKey,
                        msg.smsText().getBytes(StandardCharsets.UTF_8),
                        "text/plain; charset=utf-8"
                );
                log.warn("S3Put Bucket = {} , Key = {}", put.bucket(),put.key());

                s3Array.add(s3UrlItem("SMS", put.bucket(), put.key()));
            }

            if (s3Array.isEmpty()) {
                throw new IllegalStateException("RenderedMessage has no contents. userId=" + msg.userId());

            }


            // 3) DB 업데이트(READY + s3_url_jsonb)
            billingTargetS3UpdatePort.updateStatus(
                    billingYm,                  // "YYYY-MM"
                    msg.userId(), // 유저 Id
                    om.writeValueAsString(s3Array) // 해당 유저의 jsonB
            );
        }
    }


    /**
     * jsonb 형식으로 변환
     */
    private ObjectNode s3UrlItem(String channelKey, String bucket, String s3Key) {
        ObjectNode n = om.createObjectNode();
        n.put("key", channelKey);
        n.put("bucket", bucket);
        n.put("s3_key", s3Key);
        log.warn("ObjectNode = {}",n);
        return n;
    }


    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }


    // String -> YearMonth로 변환
    private YearMonth resolveBillingYearMonth(RenderedMessageResult msg) {
        if (hasText(msg.period())) return YearMonth.parse(msg.period());
        throw new IllegalArgumentException("RenderedMessage period/billingMonth missing. userId=" + msg.userId());
    }
}

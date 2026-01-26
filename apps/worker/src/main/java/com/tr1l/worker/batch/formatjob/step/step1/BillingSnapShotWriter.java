package com.tr1l.worker.batch.formatjob.step.step1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tr1l.billing.application.model.RenderedMessageResult;
import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import com.tr1l.billing.application.port.out.S3UploadPort;
import com.tr1l.util.EncryptionTool;
import com.tr1l.billing.application.model.RenderedMessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;

@Slf4j
@StepScope
public class BillingSnapShotWriter implements ItemWriter<RenderedMessageResult> {

    private final String bucket;
    private final S3UploadPort s3UploadPort;
    private final BillingTargetS3UpdatePort billingTargetS3UpdatePort;
    private final ObjectMapper om;
    private final Executor s3UploadExecutor;
    private final EncryptionTool encryptionTool;

    public BillingSnapShotWriter(String bucket,
                                 S3UploadPort s3UploadPort,
                                 ObjectMapper om,
                                 BillingTargetS3UpdatePort billingTargetS3UpdatePort,
                                 EncryptionTool encryptionTool,
                                 @Qualifier("s3UploadExecutor") Executor s3UploadExecutor) {
        this.bucket = bucket;
        this.s3UploadPort = s3UploadPort;
        this.om = om;
        this.billingTargetS3UpdatePort = billingTargetS3UpdatePort;
        this.s3UploadExecutor = s3UploadExecutor;
        this.encryptionTool=encryptionTool;
    }
    // 한 유저에 대한 레코드 (email, sms)
    private record MsgCtx(int index, YearMonth billingYm, long userId, ArrayNode s3Array) {}

    private record UploadOutcome(MsgCtx ctx, String channelKey, int channelIndex, S3UploadPort.S3PutResult put) {}

    // 병렬 실패 시
    private record UploadTask(MsgCtx ctx, String channelKey, int channelIndex, String key, CompletableFuture<UploadOutcome> future) {}

    @Override
    public void write(Chunk<? extends RenderedMessageResult> chunk) throws Exception {
        long t0 = System.nanoTime();
        log.error("[Job2_Writer 시작" );

        List<MsgCtx> contexts = new ArrayList<>(chunk.size());
        List<UploadTask> uploadTasks = new ArrayList<>(chunk.size() * 2);
        int index = 0;

        for (RenderedMessageResult msg : chunk) {
            YearMonth billingYm = resolveBillingYearMonth(msg);

            String base = msg.period() + "/" + msg.userId() + "/";

            String emailKey = base + "EMAIL.gz";
            String smsKey   = base + "SMS.gz";

            MsgCtx ctx = new MsgCtx(index++, billingYm, msg.userId(), om.createArrayNode());
            contexts.add(ctx);

            // EMAIL 업로드(병렬)
            if (hasText(msg.emailHtml())) {
                final String emailHtml = msg.emailHtml();
                CompletableFuture<UploadOutcome> future = CompletableFuture.supplyAsync(() -> {
                    byte[] raw = emailHtml.getBytes(StandardCharsets.UTF_8);
                    byte[] gz  = gzip(raw);
                    S3UploadPort.S3PutResult put = s3UploadPort.putGzipBytes(
                            bucket, emailKey, gz, "text/html; charset=utf-8"
                    );
                    return new UploadOutcome(ctx, "EMAIL", 0, put);
                }, s3UploadExecutor);

                uploadTasks.add(new UploadTask(ctx, "EMAIL", 0, emailKey, future));
            }

            // SMS 업로드(병렬)
            if (hasText(msg.smsText())) {
                final String smsText = msg.smsText();
                CompletableFuture<UploadOutcome> future = CompletableFuture.supplyAsync(() -> {
                    byte[] raw = smsText.getBytes(StandardCharsets.UTF_8);
                    byte[] gz  = gzip(raw);
                    S3UploadPort.S3PutResult put = s3UploadPort.putGzipBytes(
                            bucket, smsKey, gz, "text/plain; charset=utf-8"
                    );
                    return new UploadOutcome(ctx, "SMS", 1, put);
                }, s3UploadExecutor);

                uploadTasks.add(new UploadTask(ctx, "SMS", 1, smsKey, future));
            }
        }


        // 1) 먼저 한번에 완료 대기 -> 병렬성 유지
        CompletableFuture<?>[] futures = uploadTasks.stream()
                .map(UploadTask::future)
                .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(futures).join();
        } catch (RuntimeException ignore) {
            // 아래에서 에러 제어
        }

        // 2) 개별 join로 결과 수집, 실패 시 에러 로그
        S3UploadPort.S3PutResult[][] results = new S3UploadPort.S3PutResult[contexts.size()][2];
        Throwable firstFailure = null;

        for (UploadTask task : uploadTasks) {
            try {
                UploadOutcome out = task.future().join();
                results[out.ctx().index()][out.channelIndex()] = out.put();
            } catch (RuntimeException e) {
                Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                if (firstFailure == null) firstFailure = cause;

                log.error("S3 upload failed. userId={}, billingYm={}, channel={}, key={}",
                        task.ctx().userId(), task.ctx().billingYm(), task.channelKey(), task.key(), cause);
            }
        }

        if (firstFailure != null) {
            throw new IllegalStateException("S3 upload failed", firstFailure);
        }

        // 결과를 단일 스레드에서 JSON ArrayNode에 반영
        for (MsgCtx ctx : contexts) {
            S3UploadPort.S3PutResult emailPut = results[ctx.index()][0];
            if (emailPut != null) {
                ctx.s3Array().add(s3UrlItem("EMAIL", emailPut.bucket(), emailPut.key()));
            }
            S3UploadPort.S3PutResult smsPut = results[ctx.index()][1];
            if (smsPut != null) {
                ctx.s3Array().add(s3UrlItem("SMS", smsPut.bucket(), smsPut.key()));
            }
        }

        // 업데이트 요청 bulk 생성(Chunk 당 1회)
        List<BillingTargetS3UpdatePort.UpdateRequest> updates = new ArrayList<>(contexts.size());
        for (MsgCtx ctx : contexts) {
            if (ctx.s3Array().isEmpty()) {
                throw new IllegalStateException("RenderedMessage has no contents. userId=" + ctx.userId());
            }
            updates.add(new BillingTargetS3UpdatePort.UpdateRequest(
                    ctx.billingYm(),
                    ctx.userId(),
                    om.writeValueAsString(ctx.s3Array())
            ));
        }

        billingTargetS3UpdatePort.updateStatusBulk(updates);

    }

    private ObjectNode s3UrlItem(String channelKey, String bucket, String s3Key) {
        ObjectNode n = om.createObjectNode();
        n.put("key", channelKey);
        n.put("bucket", encryptionTool.encrypt(bucket));
        n.put("s3_key", encryptionTool.encrypt(s3Key));

        return n;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private YearMonth resolveBillingYearMonth(RenderedMessageResult msg) {
        if (hasText(msg.period())) return YearMonth.parse(msg.period());
        throw new IllegalArgumentException("RenderedMessage period/billingMonth missing. userId=" + msg.userId());
    }

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

package com.tr1l.worker.batch.formatjob.step.step1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tr1l.billing.application.model.RenderedMessageResult;
import com.tr1l.billing.application.port.out.BillingTargetS3UpdatePort;
import com.tr1l.billing.application.port.out.S3UploadPort;
import com.tr1l.util.EncryptionTool;
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
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPOutputStream;

@Slf4j
@StepScope
public class BillingSnapShotWriter implements ItemWriter<RenderedMessageResult> {

    private final String bucket;
    private final S3UploadPort s3UploadPort;
    private final BillingTargetS3UpdatePort billingTargetS3UpdatePort;
    private final ObjectMapper om;
    private final EncryptionTool encryptionTool;

    private final int maxConcurrency;
    private final Executor gzipExecutor;

    public BillingSnapShotWriter(String bucket,
                                 S3UploadPort s3UploadPort,
                                 ObjectMapper om,
                                 BillingTargetS3UpdatePort billingTargetS3UpdatePort,
                                 EncryptionTool encryptionTool,
                                 int maxConcurrency,
                                 Executor gzipExecutor
    ) {
        this.bucket = bucket;
        this.s3UploadPort = s3UploadPort;
        this.om = om;
        this.billingTargetS3UpdatePort = billingTargetS3UpdatePort;
        this.encryptionTool = encryptionTool;
        this.maxConcurrency = maxConcurrency;
        this.gzipExecutor = gzipExecutor;
    }

    private record MsgCtx(int index, YearMonth billingYm, long userId, ArrayNode s3Array) {}
    private record UploadOutcome(MsgCtx ctx, String channelKey, int channelIndex, S3UploadPort.S3PutResult put) {}
    private record UploadTask(MsgCtx ctx, String channelKey, int channelIndex, String key, CompletableFuture<UploadOutcome> future) {}


    private final java.util.concurrent.atomic.AtomicInteger inflight = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger maxInflight = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public void write(Chunk<? extends RenderedMessageResult> chunk) throws Exception {
        Semaphore sem = new Semaphore(maxConcurrency);


        long t0 = System.nanoTime();
        log.info("[Job2_Writer 시작] chunkSize={}, maxConcurrency={}", chunk.size(), maxConcurrency);

        // 청크 단위 측정 리셋
        inflight.set(0);
        maxInflight.set(0);

        List<MsgCtx> contexts = new ArrayList<>(chunk.size());
        List<UploadTask> uploadTasks = new ArrayList<>(chunk.size() * 2);

        int index = 0;

        for (RenderedMessageResult msg : chunk) {
            YearMonth billingYm = resolveBillingYearMonth(msg);

            String base = msg.period() + "/" + msg.userId() + "/";
            String emailKey = base + "EMAIL.gz";
            String smsKey   = base + "SMS.txt"; // sms 압축 진행 x

            MsgCtx ctx = new MsgCtx(index++, billingYm, msg.userId(), om.createArrayNode());
            contexts.add(ctx);

            // EMAIL 업로드(병렬) -> gzip은 gzipExecutor에서, 업로드만 세마포어로 조절
            if (hasText(msg.emailHtml())) {
                final String emailHtml = msg.emailHtml();

                CompletableFuture<UploadOutcome> future =
                        CompletableFuture
                                .supplyAsync(() -> {
                                    byte[] raw = emailHtml.getBytes(StandardCharsets.UTF_8);
                                    return gzip(raw);
                                }, gzipExecutor)
                                // 업로드 단계만 backpressure
                                .thenCompose(gz ->
                                        startUpload(sem, () ->
                                                s3UploadPort.putGzipBytesAsync(
                                                        bucket, emailKey, gz, "text/html; charset=utf-8"
                                                )
                                        )
                                )
                                .thenApply(put -> new UploadOutcome(ctx, "EMAIL", 0, put));

                uploadTasks.add(new UploadTask(ctx, "EMAIL", 0, emailKey, future));
            }

            // SMS 업로드(병렬) -> 압축 안함
            if (hasText(msg.smsText())) {
                final String smsText = msg.smsText();
                byte[] raw = smsText.getBytes(StandardCharsets.UTF_8);

                CompletableFuture<UploadOutcome> future =
                        startUpload(sem, () ->
                                s3UploadPort.putBytesAsync(bucket, smsKey, raw, "text/plain; charset=utf-8")
                        ).thenApply(put -> new UploadOutcome(ctx, "SMS", 1, put));

                uploadTasks.add(new UploadTask(ctx, "SMS", 1, smsKey, future));
            }
        }

        // 1) 전체 완료 대기
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
        int failureCount = 0;

        Long firstUserId = null;
        YearMonth firstBillingYm = null;
        String firstChannel = null;
        String firstKey = null;

        for (UploadTask task : uploadTasks) {
            try {
                UploadOutcome out = task.future().join();
                results[out.ctx().index()][out.channelIndex()] = out.put();
            } catch (RuntimeException e) {
                Throwable cause = (e.getCause() != null) ? e.getCause() : e;

                // 개별 실패 상세는 DEBUG로(청크당 ERROR 1줄 정책)
                log.debug("S3 upload failed. userId={}, billingYm={}, channel={}, key={}",
                        task.ctx().userId(), task.ctx().billingYm(), task.channelKey(), task.key(), cause);

                failureCount++;

                if (firstFailure == null) {
                    firstFailure = cause;
                    firstUserId = task.ctx().userId();
                    firstBillingYm = task.ctx().billingYm();
                    firstChannel = task.channelKey();
                    firstKey = task.key();
                }
            }
        }

        if (firstFailure != null) {
            // ERROR는 청크당 1번만
            log.error("[S3 upload failed in chunk] chunkSize={}, tasks={}, failures={}, peakInflight={}, first(userId={}, billingYm={}, channel={}, key={})",
                    chunk.size(),
                    uploadTasks.size(),
                    failureCount,
                    maxInflight.get(),
                    firstUserId, firstBillingYm, firstChannel, firstKey,
                    firstFailure);

            throw new IllegalStateException("S3 upload failed", firstFailure);
        }

        // 결과를 JSON ArrayNode에 반영
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

        billingTargetS3UpdatePort.updateStatusBulkSingleQuery(updates);

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("[Job2_Writer 완료] chunkSize={}, tasks={}, peakInflight={}, elapsedMs={}",
                chunk.size(), uploadTasks.size(), maxInflight.get(), elapsedMs);
    }

    private <T> CompletableFuture<T> startUpload(Semaphore sem, java.util.function.Supplier<CompletableFuture<T>> supplier) {
        sem.acquireUninterruptibly();
        markStart();
        try {
            return supplier.get().whenComplete((ok, ex) -> {
                markEnd();
                sem.release();
            });
        } catch (Throwable t) {
            markEnd();
            sem.release();
            return CompletableFuture.failedFuture(t);
        }
    }



    private CompletableFuture<UploadOutcome> startAsync(Semaphore sem, AsyncFutureSupplier supplier) {
        // 동시 업로드 제한을 두는 백프레셔
        sem.acquireUninterruptibly();

        // 측정 시작
        markStart();
        try {
            return supplier.get()
                    .whenComplete((ok, ex) -> {
                        // 3) 측정 종료 + permit 반환 (성공/실패 무조건)
                        markEnd();
                        sem.release();
                    });
        } catch (Throwable t) {
            // supplier.get() 자체가 즉시 예외면 여기로 옴
            markEnd();
            sem.release();
            return CompletableFuture.failedFuture(t);
        }
    }

    @FunctionalInterface
    private interface AsyncFutureSupplier {
        CompletableFuture<UploadOutcome> get();
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

    private void markStart() {
        int now = inflight.incrementAndGet();
        maxInflight.accumulateAndGet(now, Math::max);
    }

    private void markEnd() {
        inflight.decrementAndGet();
    }
}

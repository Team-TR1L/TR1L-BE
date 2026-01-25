package com.tr1l.worker.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.listener.ChunkListenerSupport;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;

import java.util.function.Function;

/**
 * =========================================================
 * PerfTimingListener
 *
 * 목적:
 * - Step 단위 성능 측정(READ/PROCESS/WRITE)
 * - Chunk 단위 요약 로그(CHUNK)
 * - Step 전체 요약 로그(TOTAL_TIMING)
 * - 느린 구간만 WARN (SLOW_READ / SLOW_PROCESS / SLOW_WRITE / SLOW_CHUNK)
 *
 * 주의:
 * - Listener는 절대 배치를 죽이면 안 됨.
 * => stepExecution null 방어, 예외 삼키기(로그만 남기기)
 * =========================================================
 */
@Slf4j
public class PerfTimingListener<I, O> extends ChunkListenerSupport
        implements ItemReadListener<I>, ItemProcessListener<I, O>, ItemWriteListener<O>, StepExecutionListener {

    // -----------------------------
    // StepExecution (현재 step 상태)
    // -----------------------------
    private StepExecution stepExecution;

    // -----------------------------
    // ExecutionContext keys - chunk 누적용(ns)
    // (청크 끝나면 리셋)
    // -----------------------------
    private static final String READ_NS  = "perf.readNs";
    private static final String PROC_NS  = "perf.procNs";
    private static final String WRITE_NS = "perf.writeNs";

    // ExecutionContext keys - chunk 누적용(count)
    private static final String READ_CNT  = "perf.readCnt";
    private static final String PROC_CNT  = "perf.procCnt";
    private static final String WRITE_CNT = "perf.writeCnt";

    // Processor가 null 반환한 수(=writer로 안 감)
    // 기본은 "스텝 전체 누적" (청크별로 보고 싶으면 별도 키로 분리 권장)
    private static final String FILTER_CNT = "perf.filterCnt";

    // -----------------------------
    // ExecutionContext keys - step 전체 누적용(ns)
    // (스텝 끝날 때 TOTAL 로그로 출력)
    // -----------------------------
    private static final String TOTAL_READ_NS = "perf.totalReadNs";
    private static final String TOTAL_PROC_NS = "perf.totalProcNs";
    private static final String TOTAL_WRITE_NS = "perf.totalWriteNs";

    // -----------------------------
    // Slow threshold (ms)
    // -----------------------------
    private final long slowReadMs;
    private final long slowProcessMs;
    private final long slowWriteMs;
    private final long slowChunkMs; // chunk 전체가 느릴 때 WARN 승격

    /**
     * item에서 안전하게 "식별자(id)"만 꺼내기 위한 함수
     * 예) doc -> doc.id()
     */
    private final Function<I, String> itemIdExtractor;

    // -----------------------------
    // 타이밍 측정용 (nanoTime)
    // - ThreadLocal: beforeX에서 시작 찍고 afterX에서 duration 계산
    // -----------------------------
    private final ThreadLocal<Long> readStart  = new ThreadLocal<>();
    private final ThreadLocal<Long> procStart  = new ThreadLocal<>();
    private final ThreadLocal<Long> writeStart = new ThreadLocal<>();
    private final ThreadLocal<Long> chunkStart = new ThreadLocal<>();

    public PerfTimingListener(
            long slowReadMs,
            long slowProcessMs,
            long slowWriteMs,
            long slowChunkMs,
            Function<I, String> itemIdExtractor
    ) {
        this.slowReadMs = slowReadMs;
        this.slowProcessMs = slowProcessMs;
        this.slowWriteMs = slowWriteMs;
        this.slowChunkMs = slowChunkMs;
        this.itemIdExtractor = itemIdExtractor;
    }

    // =========================================================
    // StepExecutionListener (가장 안전하게 before/after 보장)
    // =========================================================

    /**
     * Step 시작 시 1회 호출
     * - 누적값 초기화(청크용 + 스텝전체용)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;

        ExecutionContext ctx = stepExecution.getExecutionContext();

        // 스텝 전체 누적(총 시간) 초기화
        ctx.putLong(TOTAL_READ_NS, 0L);
        ctx.putLong(TOTAL_PROC_NS, 0L);
        ctx.putLong(TOTAL_WRITE_NS, 0L);

        // filter 누적 초기화(스텝 전체로 집계)
        ctx.putLong(FILTER_CNT, 0L);

        // 청크 누적 초기화
        resetChunkAccumulatorsSafe();
    }

    /**
     * Step 종료 시 1회 호출
     * - 스텝 전체 누적 read/proc/write 총시간을 출력 (TOTAL)
     * - commit/rollback/skip 같은 운영 지표도 같이 출력
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            ExecutionContext ctx = stepExecution.getExecutionContext();

            long totalReadMs  = ctx.getLong(TOTAL_READ_NS, 0L) / 1_000_000;
            long totalProcMs  = ctx.getLong(TOTAL_PROC_NS, 0L) / 1_000_000;
            long totalWriteMs = ctx.getLong(TOTAL_WRITE_NS, 0L) / 1_000_000;

            long filterCnt = ctx.getLong(FILTER_CNT, 0L);

            log.warn("""
                            ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                            ┃           STEP TOTAL TIMING (SUMMARY)            ┃
                            ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
                            ┃ step       : {}                                  ┃
                            ┃ status     : {}                                  ┃
                            ┃ read       : {} ms   (count={})                  ┃
                            ┃ process    : {} ms                               ┃
                            ┃ write      : {} ms   (count={})                  ┃
                            ┃ filter     : {}                                  ┃
                            ┃ skip       : {}                                  ┃
                            ┃ commit     : {}                                  ┃
                            ┃ rollback   : {}                                  ┃
                            ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                            """,
                    stepExecution.getStepName(),
                    stepExecution.getExitStatus().getExitCode(),
                    totalReadMs, stepExecution.getReadCount(),
                    totalProcMs,
                    totalWriteMs, stepExecution.getWriteCount(),
                    filterCnt,
                    stepExecution.getSkipCount(),
                    stepExecution.getCommitCount(),
                    stepExecution.getRollbackCount()
            );
        } catch (Exception e) {
            // Listener가 배치를 죽이면 안 됨
            log.error("step={} TOTAL_TIMING_LOG_FAILED err={}", safeStepName(stepExecution), e.toString(), e);
        }

        return stepExecution.getExitStatus();
    }

    // =========================================================
    // Chunk lifecycle
    // =========================================================

    /**
     * 청크 시작 시각 기록
     */
    @Override
    public void beforeChunk(ChunkContext context) {
        chunkStart.set(System.nanoTime());
    }

    /**
     * 청크 종료 시 "청크 요약" 로그 1줄 출력
     */
    @Override
    public void afterChunk(ChunkContext context) {
        // stepExecution이 아직 없으면(이상 케이스) 그냥 무시
        if (this.stepExecution == null) {
            log.warn("step=unknown-step CHUNK skipped: stepExecution is null");
            return;
        }

        long start = chunkStart.get() == null ? System.nanoTime() : chunkStart.get();
        long chunkElapsedMs = (System.nanoTime() - start) / 1_000_000;

        ExecutionContext ctx = this.stepExecution.getExecutionContext();

        // 청크 누적 시간(ms)
        long readMs = ctx.getLong(READ_NS, 0L) / 1_000_000;
        long procMs = ctx.getLong(PROC_NS, 0L) / 1_000_000;
        long writeMs = ctx.getLong(WRITE_NS, 0L) / 1_000_000;

        // 청크 건수
        long readCnt = ctx.getLong(READ_CNT, 0L);
        long procCnt = ctx.getLong(PROC_CNT, 0L);
        long writeCnt = ctx.getLong(WRITE_CNT, 0L);

        // filter는 스텝 누적값
        long filterCnt = ctx.getLong(FILTER_CNT, 0L);

        // 평균(ms/item)
        double readAvg = (readCnt == 0) ? 0 : (double) readMs / readCnt;
        double procAvg = (procCnt == 0) ? 0 : (double) procMs / procCnt;
        double writeAvg = (writeCnt == 0) ? 0 : (double) writeMs / writeCnt;

        // TPS (write 기준)
        double tps = (chunkElapsedMs == 0) ? 0 : (writeCnt * 1000.0 / chunkElapsedMs);

        String msg = String.format(
                "step=%s chunkMs=%d readMs=%d readCnt=%d readAvg=%.3f " +
                        "procMs=%d procCnt=%d procAvg=%.3f filter=%d " +
                        "writeMs=%d writeCnt=%d writeAvg=%.3f tps=%.1f " +
                        "totalRead=%d totalWrite=%d skip=%d",
                this.stepExecution.getStepName(),
                chunkElapsedMs,
                readMs, readCnt, readAvg,
                procMs, procCnt, procAvg, filterCnt,
                writeMs, writeCnt, writeAvg, tps,
                this.stepExecution.getReadCount(),
                this.stepExecution.getWriteCount(),
                this.stepExecution.getSkipCount()
        );

        if (slowChunkMs > 0 && chunkElapsedMs >= slowChunkMs) {
            log.info("SLOW_CHUNK {}", msg);
        } else {
            log.info("CHUNK {}", msg);
        }

        // 다음 청크를 위해 chunk 누적값 리셋
        resetChunkAccumulatorsSafe();
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        log.error("step={} CHUNK_ERROR", stepName());
    }

    // =========================================================
    // Read timing
    // =========================================================

    @Override
    public void beforeRead() {
        readStart.set(System.nanoTime());
    }

    @Override
    public void afterRead(I item) {
        long start = readStart.get() == null ? System.nanoTime() : readStart.get();
        long elapsedNs = System.nanoTime() - start;

        // 청크 누적 + 스텝 전체 누적
        addSafe(READ_NS, elapsedNs);
        addSafe(TOTAL_READ_NS, elapsedNs);

        // 청크 건수 증가
        incSafe(READ_CNT);

        // 느린 read만 WARN
        long elapsedMs = elapsedNs / 1_000_000;
        if (elapsedMs >= slowReadMs) {
            log.info("step={} SLOW_READ {}ms itemId={}", stepName(), elapsedMs, safeId(item));
        }
    }

    @Override
    public void onReadError(Exception ex) {
        // 여기서 절대 NPE 나면 안 됨
        log.error("step={} READ_ERROR", stepName(), ex);
    }

    // =========================================================
    // Process timing
    // =========================================================

    @Override
    public void beforeProcess(I item) {
        procStart.set(System.nanoTime());
    }

    @Override
    public void afterProcess(I item, O result) {
        long start = procStart.get() == null ? System.nanoTime() : procStart.get();
        long elapsedNs = System.nanoTime() - start;

        // 청크 누적 + 스텝 전체 누적
        addSafe(PROC_NS, elapsedNs);
        addSafe(TOTAL_PROC_NS, elapsedNs);

        // 청크 건수 증가
        incSafe(PROC_CNT);

        // processor가 null 반환하면 filter로 카운트
        if (result == null) {
            incSafe(FILTER_CNT);
        }

        long elapsedMs = elapsedNs / 1_000_000;
        if (elapsedMs >= slowProcessMs) {
            log.info("step={} SLOW_PROCESS {}ms itemId={}", stepName(), elapsedMs, safeId(item));
        }
    }

    @Override
    public void onProcessError(I item, Exception e) {
        log.error("step={} PROCESS_ERROR itemId={}", stepName(), safeId(item), e);
    }

    // =========================================================
    // Write timing (Spring Batch 5: Chunk 기반 시그니처)
    // =========================================================

    @Override
    public void beforeWrite(Chunk<? extends O> items) {
        writeStart.set(System.nanoTime());
    }

    @Override
    public void afterWrite(Chunk<? extends O> items) {
        long start = writeStart.get() == null ? System.nanoTime() : writeStart.get();
        long elapsedNs = System.nanoTime() - start;

        addSafe(WRITE_NS, elapsedNs);
        addSafe(TOTAL_WRITE_NS, elapsedNs);

        int size = (items == null) ? 0 : items.size();
        addLongSafe(WRITE_CNT, size);

        long elapsedMs = elapsedNs / 1_000_000;
        if (elapsedMs >= slowWriteMs) {
            log.info("step={} SLOW_WRITE {}ms size={}", stepName(), elapsedMs, size);
        }
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends O> items) {
        int size = (items == null) ? 0 : items.size();
        log.error("step={} WRITE_ERROR size={}", stepName(), size, exception);
    }

    // =========================================================
    // helpers (null-safe)
    // =========================================================

    /**
     * stepExecution이 null일 수도 있으니 안전하게 stepName 반환
     */
    private String stepName() {
        return (stepExecution == null) ? "unknown-step" : stepExecution.getStepName();
    }

    private String safeStepName(StepExecution se) {
        return (se == null) ? "unknown-step" : se.getStepName();
    }

    /**
     * 청크별 누적값 리셋 (stepExecution null-safe)
     */
    private void resetChunkAccumulatorsSafe() {
        if (stepExecution == null) return;
        ExecutionContext ctx = stepExecution.getExecutionContext();

        ctx.putLong(READ_NS, 0L);
        ctx.putLong(PROC_NS, 0L);
        ctx.putLong(WRITE_NS, 0L);

        ctx.putLong(READ_CNT, 0L);
        ctx.putLong(PROC_CNT, 0L);
        ctx.putLong(WRITE_CNT, 0L);
    }

    /**
     * ExecutionContext에 long 누적 더하기 (stepExecution null-safe)
     */
    private void addSafe(String key, long delta) {
        if (stepExecution == null) return;
        ExecutionContext ctx = stepExecution.getExecutionContext();
        ctx.putLong(key, ctx.getLong(key, 0L) + delta);
    }

    /**
     * ExecutionContext long 값 1 증가 (stepExecution null-safe)
     */
    private void incSafe(String key) {
        if (stepExecution == null) return;
        ExecutionContext ctx = stepExecution.getExecutionContext();
        ctx.putLong(key, ctx.getLong(key, 0L) + 1);
    }

    /**
     * ExecutionContext에 특정 delta 만큼 증가 (stepExecution null-safe)
     */
    private void addLongSafe(String key, long delta) {
        if (stepExecution == null) return;
        ExecutionContext ctx = stepExecution.getExecutionContext();
        ctx.putLong(key, ctx.getLong(key, 0L) + delta);
    }

    /**
     * itemIdExtractor가 실패해도 배치가 죽으면 안 된다.
     * - 예외는 삼키고 unknown 처리.
     */
    private String safeId(I item) {
        try {
            return item == null ? "null" : itemIdExtractor.apply(item);
        } catch (Exception e) {
            return "unknown";
        }
    }
}

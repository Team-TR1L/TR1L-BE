package com.tr1l.worker.config.job;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class MongoQueryTimingCommandListener implements CommandListener {

    // 어떤 커맨드만 찍을지 (원하면 더 추가 가능)
    private static final Set<String> TARGET = Set.of("find", "aggregate", "getMore", "count");

    // requestId -> timing context
    private final ConcurrentHashMap<Integer, TimingContext> timings = new ConcurrentHashMap<>();

    // 너무 자주 찍히는 getMore는 느릴 때만 찍고 싶으면 threshold로 제어
    private final long slowMs;
    private final MeterRegistry meterRegistry;

    public MongoQueryTimingCommandListener(MeterRegistry meterRegistry, long slowMs) {
        this.meterRegistry = meterRegistry;
        this.slowMs = slowMs;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        String cmd = event.getCommandName();
        if (!TARGET.contains(cmd)) return;

        timings.put(event.getRequestId(), new TimingContext(
                System.nanoTime(),
                cmd,
                event.getDatabaseName(),
                extractCollection(cmd, event.getCommand())
        ));

        // 필요하면 시작 로그도 찍을 수 있는데 보통은 종료만 찍는 게 깔끔함
        // log.debug("mongo START cmd={} db={} {}", cmd, event.getDatabaseName(), shrink(event.getCommand()));
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        String cmd = event.getCommandName();
        if (!TARGET.contains(cmd)) return;

        TimingContext ctx = timings.remove(event.getRequestId());
        if (ctx == null) return;

        long elapsedMs = (System.nanoTime() - ctx.startNs) / 1_000_000;

        // getMore는 호출이 많아서 보통 "느릴 때만" 찍는 걸 추천
        if ("getMore".equals(cmd) && elapsedMs < slowMs) return;

        recordMetric(ctx, elapsedMs, true);

        if (elapsedMs >= slowMs) {
            log.warn("MONGO_SLOW cmd={} ms={} db={} reqId={}", cmd, elapsedMs, event.getDatabaseName(), event.getRequestId());
        } else {
            log.info("MONGO_QUERY cmd={} ms={} db={} reqId={}", cmd, elapsedMs, event.getDatabaseName(), event.getRequestId());
        }
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        String cmd = event.getCommandName();
        if (!TARGET.contains(cmd)) return;

        TimingContext ctx = timings.remove(event.getRequestId());
        long elapsedMs = (ctx == null) ? -1 : (System.nanoTime() - ctx.startNs) / 1_000_000;

        if (ctx != null) {
            recordMetric(ctx, elapsedMs, false);
        }

        log.error("MONGO_FAIL cmd={} ms={} db={} reqId={} err={}",
                cmd, elapsedMs, event.getDatabaseName(), event.getRequestId(), event.getThrowable().toString());
    }

    // (옵션) 커맨드 내용을 찍고 싶을 때 사용 — 근데 너무 커질 수 있어서 기본은 비추
    private String shrink(BsonDocument doc) {
        String s = doc.toJson();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private void recordMetric(TimingContext ctx, long elapsedMs, boolean success) {
        if (meterRegistry == null) return;

        Timer.builder("mongo.command.duration")
                .tag("cmd", ctx.cmd)
                .tag("db", ctx.db)
                .tag("collection", ctx.collection == null ? "unknown" : ctx.collection)
                .tag("status", success ? "ok" : "error")
                .register(meterRegistry)
                .record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private String extractCollection(String cmd, BsonDocument doc) {
        if (doc == null) return null;

        if ("getMore".equals(cmd)) {
            if (doc.containsKey("collection")) return doc.getString("collection").getValue();
            return null;
        }

        if (doc.containsKey(cmd)) {
            return doc.getString(cmd).getValue();
        }

        return null;
    }

    private record TimingContext(long startNs, String cmd, String db, String collection) {}
}

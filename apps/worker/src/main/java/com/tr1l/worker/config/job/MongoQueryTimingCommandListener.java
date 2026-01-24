package com.tr1l.worker.config.job;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class MongoQueryTimingCommandListener implements CommandListener {

    // 어떤 커맨드만 찍을지 (원하면 더 추가 가능)
    private static final Set<String> TARGET = Set.of("find", "aggregate", "getMore", "count");

    // requestId -> startNano 저장
    private final ConcurrentHashMap<Integer, Long> startNs = new ConcurrentHashMap<>();

    // 너무 자주 찍히는 getMore는 느릴 때만 찍고 싶으면 threshold로 제어
    private final long slowMs;

    public MongoQueryTimingCommandListener(long slowMs) {
        this.slowMs = slowMs;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        String cmd = event.getCommandName();
        if (!TARGET.contains(cmd)) return;

        startNs.put(event.getRequestId(), System.nanoTime());

        // 필요하면 시작 로그도 찍을 수 있는데 보통은 종료만 찍는 게 깔끔함
        // log.debug("mongo START cmd={} db={} {}", cmd, event.getDatabaseName(), shrink(event.getCommand()));
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        String cmd = event.getCommandName();
        if (!TARGET.contains(cmd)) return;

        Long s = startNs.remove(event.getRequestId());
        if (s == null) return;

        long elapsedMs = (System.nanoTime() - s) / 1_000_000;

        // getMore는 호출이 많아서 보통 "느릴 때만" 찍는 걸 추천
        if ("getMore".equals(cmd) && elapsedMs < slowMs) return;

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

        Long s = startNs.remove(event.getRequestId());
        long elapsedMs = (s == null) ? -1 : (System.nanoTime() - s) / 1_000_000;

        log.error("MONGO_FAIL cmd={} ms={} db={} reqId={} err={}",
                cmd, elapsedMs, event.getDatabaseName(), event.getRequestId(), event.getThrowable().toString());
    }

    // (옵션) 커맨드 내용을 찍고 싶을 때 사용 — 근데 너무 커질 수 있어서 기본은 비추
    private String shrink(BsonDocument doc) {
        String s = doc.toJson();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}


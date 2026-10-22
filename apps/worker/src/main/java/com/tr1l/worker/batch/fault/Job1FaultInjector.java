package com.tr1l.worker.batch.fault;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

// Job1 재실행 검증용 장애 주입기
// app.reliability.fault.point 가 비어 있으면 장애를 내지 않는다
//   point     주입 지점 (P0 ~ P4)
//   nth       그 지점을 몇 번째로 지날 때 주입할지. 1부터 센다
//   repeat    ONCE 는 nth 번째 한 번만, FROM_NTH 는 nth 번째부터 계속
//   kind      EXCEPTION 예외, HALT 프로세스 즉시 종료(kill -9 와 같이 shutdown hook 도 돌지 않음),
//             PAUSE 표시 파일을 남기고 재개 파일이 생길 때까지 멈춤 (그 사이 하네스가 DB 연결을 끊는다)
//   pause-dir PAUSE 에서 쓰는 디렉터리. paused 파일을 만들고 resume 파일을 기다린다
//   hits-file 값이 있으면 종료할 때 지점별 통과 횟수를 JSON 으로 남긴다
//             하네스가 MIDDLE, LAST 시점을 몇 번째 호출로 바꿀 때 쓴다
@Component
public class Job1FaultInjector {
    // PAUSE 에서 재개를 기다리는 최대 시간
    private static final Duration PAUSE_TIMEOUT = Duration.ofMinutes(2);

    private final Job1FaultPoint point;
    private final long nth;
    private final Repeat repeat;
    private final Kind kind;
    private final String pauseDir;
    private final String hitsFile;
    private final Runnable halt;
    private final Map<Job1FaultPoint, AtomicLong> hits = new EnumMap<>(Job1FaultPoint.class);

    @Autowired
    public Job1FaultInjector(
            @Value("${app.reliability.fault.point:}") String point,
            @Value("${app.reliability.fault.nth:1}") long nth,
            @Value("${app.reliability.fault.repeat:ONCE}") String repeat,
            @Value("${app.reliability.fault.kind:EXCEPTION}") String kind,
            @Value("${app.reliability.fault.pause-dir:}") String pauseDir,
            @Value("${app.reliability.fault.hits-file:}") String hitsFile
    ) {
        this(point, nth, repeat, kind, pauseDir, hitsFile, () -> Runtime.getRuntime().halt(137));
    }

    Job1FaultInjector(
            String point, long nth, String repeat, String kind, String pauseDir, String hitsFile, Runnable halt
    ) {
        this.point = point == null || point.isBlank() ? null : Job1FaultPoint.valueOf(point.trim());
        this.nth = Math.max(nth, 1);
        this.repeat = Repeat.valueOf(repeat.trim());
        this.kind = Kind.valueOf(kind.trim());
        this.pauseDir = pauseDir;
        this.hitsFile = hitsFile;
        this.halt = halt;
        for (Job1FaultPoint value : Job1FaultPoint.values()) {
            hits.put(value, new AtomicLong());
        }
    }

    public Job1FaultInjector(String point, long nth, String repeat, String hitsFile) {
        this(point, nth, repeat, "EXCEPTION", "", hitsFile, () -> Runtime.getRuntime().halt(137));
    }

    public Job1FaultInjector(String point, long nth, String repeat) {
        this(point, nth, repeat, "");
    }

    // 지점을 지날 때마다 부른다. 설정과 맞으면 장애를 낸다
    public void hit(Job1FaultPoint current) {
        long count = hits.get(current).incrementAndGet();
        if (current != point) {
            return;
        }
        boolean inject = repeat == Repeat.ONCE ? count == nth : count >= nth;
        if (!inject) {
            return;
        }
        switch (kind) {
            case EXCEPTION -> throw new InjectedFaultException(current, count);
            case HALT -> halt.run();
            case PAUSE -> pause(current, count);
        }
    }

    public long hitCount(Job1FaultPoint value) {
        return hits.get(value).get();
    }

    // 표시 파일을 남기고 재개 파일을 기다린다
    private void pause(Job1FaultPoint current, long count) {
        Path dir = Path.of(pauseDir);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("paused"), current + " #" + count + "\n", StandardCharsets.UTF_8);
            long deadline = System.nanoTime() + PAUSE_TIMEOUT.toNanos();
            while (!Files.exists(dir.resolve("resume"))) {
                if (System.nanoTime() > deadline) {
                    throw new InjectedFaultException(current, count);
                }
                Thread.sleep(100);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to pause at " + current, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InjectedFaultException(current, count);
        }
    }

    @PreDestroy
    void writeHits() throws IOException {
        if (hitsFile == null || hitsFile.isBlank()) {
            return;
        }
        StringJoiner json = new StringJoiner(", ", "{", "}");
        hits.forEach((key, value) -> json.add("\"" + key + "\": " + value.get()));
        Path path = Path.of(hitsFile);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, json + "\n", StandardCharsets.UTF_8);
    }

    public enum Repeat {
        ONCE,
        FROM_NTH
    }

    public enum Kind {
        EXCEPTION,
        HALT,
        PAUSE
    }

    // 주입한 장애임을 로그에서 구분하기 위한 예외
    public static final class InjectedFaultException extends RuntimeException {
        public InjectedFaultException(Job1FaultPoint point, long count) {
            super("Injected Job1 fault at " + point + " #" + count);
        }
    }
}

package com.tr1l.worker.batch.fault;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

// Job1 재실행 검증용 장애 주입기
// app.reliability.fault.point 가 비어 있으면 예외를 던지지 않는다
//   point     주입 지점 (P0 ~ P4)
//   nth       그 지점을 몇 번째로 지날 때 주입할지. 1부터 센다
//   repeat    ONCE 는 nth 번째 한 번만, FROM_NTH 는 nth 번째부터 계속
//   hits-file 값이 있으면 종료할 때 지점별 통과 횟수를 JSON 으로 남긴다
//             하네스가 MIDDLE, LAST 시점을 몇 번째 호출로 바꿀 때 쓴다
@Component
public class Job1FaultInjector {
    private final Job1FaultPoint point;
    private final long nth;
    private final Repeat repeat;
    private final String hitsFile;
    private final Map<Job1FaultPoint, AtomicLong> hits = new EnumMap<>(Job1FaultPoint.class);

    @Autowired
    public Job1FaultInjector(
            @Value("${app.reliability.fault.point:}") String point,
            @Value("${app.reliability.fault.nth:1}") long nth,
            @Value("${app.reliability.fault.repeat:ONCE}") String repeat,
            @Value("${app.reliability.fault.hits-file:}") String hitsFile
    ) {
        this.point = point == null || point.isBlank() ? null : Job1FaultPoint.valueOf(point.trim());
        this.nth = Math.max(nth, 1);
        this.repeat = Repeat.valueOf(repeat.trim());
        this.hitsFile = hitsFile;
        for (Job1FaultPoint value : Job1FaultPoint.values()) {
            hits.put(value, new AtomicLong());
        }
    }

    public Job1FaultInjector(String point, long nth, String repeat) {
        this(point, nth, repeat, "");
    }

    // 지점을 지날 때마다 부른다. 설정과 맞으면 예외를 던진다
    public void hit(Job1FaultPoint current) {
        long count = hits.get(current).incrementAndGet();
        if (current != point) {
            return;
        }
        boolean inject = repeat == Repeat.ONCE ? count == nth : count >= nth;
        if (inject) {
            throw new InjectedFaultException(current, count);
        }
    }

    public long hitCount(Job1FaultPoint value) {
        return hits.get(value).get();
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

    // 주입한 장애임을 로그에서 구분하기 위한 예외
    public static final class InjectedFaultException extends RuntimeException {
        public InjectedFaultException(Job1FaultPoint point, long count) {
            super("Injected Job1 fault at " + point + " #" + count);
        }
    }
}

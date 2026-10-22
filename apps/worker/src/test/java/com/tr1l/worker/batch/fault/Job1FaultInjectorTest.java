package com.tr1l.worker.batch.fault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Job1FaultInjectorTest {

    @Test
    @DisplayName("지점을 정하지 않으면 예외 없이 통과 횟수만 센다")
    void disabledInjectorOnlyCountsHits() {
        Job1FaultInjector injector = new Job1FaultInjector("", 1, "ONCE");

        assertThatCode(() -> {
            injector.hit(Job1FaultPoint.P2);
            injector.hit(Job1FaultPoint.P2);
            injector.hit(Job1FaultPoint.P3c);
        }).doesNotThrowAnyException();
        assertThat(injector.hitCount(Job1FaultPoint.P2)).isEqualTo(2);
        assertThat(injector.hitCount(Job1FaultPoint.P3c)).isEqualTo(1);
    }

    @Test
    @DisplayName("ONCE 는 n 번째 한 번만 던지고 다른 지점은 영향을 주지 않는다")
    void onceThrowsOnlyAtNthHit() {
        Job1FaultInjector injector = new Job1FaultInjector("P2", 2, "ONCE");

        injector.hit(Job1FaultPoint.P1);
        injector.hit(Job1FaultPoint.P1);
        injector.hit(Job1FaultPoint.P2);
        assertThatThrownBy(() -> injector.hit(Job1FaultPoint.P2))
                .isInstanceOf(Job1FaultInjector.InjectedFaultException.class)
                .hasMessage("Injected Job1 fault at P2 #2");
        assertThatCode(() -> injector.hit(Job1FaultPoint.P2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FROM_NTH 는 n 번째부터 계속 던진다")
    void fromNthKeepsThrowing() {
        Job1FaultInjector injector = new Job1FaultInjector("P3c", 1, "FROM_NTH");

        assertThatThrownBy(() -> injector.hit(Job1FaultPoint.P3c)).isInstanceOf(Job1FaultInjector.InjectedFaultException.class);
        assertThatThrownBy(() -> injector.hit(Job1FaultPoint.P3c)).isInstanceOf(Job1FaultInjector.InjectedFaultException.class);
    }

    @Test
    @DisplayName("hits-file 을 주면 종료할 때 지점별 통과 횟수를 남긴다")
    void writesHitCountsOnShutdown(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hits.json");
        Job1FaultInjector injector = new Job1FaultInjector("", 1, "ONCE", file.toString());
        injector.hit(Job1FaultPoint.P1);
        injector.hit(Job1FaultPoint.P4);

        injector.writeHits();

        assertThat(Files.readString(file))
                .contains("\"P1\": 1")
                .contains("\"P4\": 1")
                .contains("\"P0\": 0");
    }
}

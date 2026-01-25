package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.DispatchCommand;
import com.tr1l.dispatch.domain.model.vo.DispatchResult;
import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchAsyncExecutor {

    private final DispatchEventPublisher eventPublisher;

    // ExecutorService 생성 (병렬 처리용) - 빈 생명주기 동안 유지
    private final ExecutorService executor =
            Executors.newFixedThreadPool(50); // IO 밀집 작업을 위해 스레드 수 상향 조정

    public DispatchResult execute(List<DispatchCommand> commands) {

        AtomicInteger messagesCnt = new AtomicInteger();
        AtomicInteger failedMessagesCnt = new AtomicInteger();

        // CompletableFuture 리스트로 비동기 작업 관리
        List<CompletableFuture<Void>> futures = commands.stream()
                .map(command -> CompletableFuture.runAsync(() -> {
                    try {
                        // Kafka 전송 완료까지 대기
                        eventPublisher.publish(
                                command.userId(),
                                command.billingMonth(),
                                command.channelType(),
                                command.encryptedS3Buket(),
                                command.encryptedS3Key(),
                                command.destination()
                        );

                        messagesCnt.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("❌ 카프카 메시지 발행 실패 userId: {}", command.userId()
                        );
                        failedMessagesCnt.incrementAndGet();
                    }
                }, executor))
                .toList();

        // 제출된 모든 작업 완료 대기
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception ignored) {
            // 이미 개별 작업에서 실패 처리했으므로 여기서는 무시
        }

        // 버퍼에 남은 메시지 flush
        eventPublisher.flush();

        return new DispatchResult(
                messagesCnt.get(),
                failedMessagesCnt.get()
        );
    }

    public void shutdown() throws InterruptedException {
        // Executor 종료 - 실제 애플리기케이션 종료 시 호출되도록 설계 변경 필요 (현재는 오케스트레이션에서 호출 중)
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
    }
}
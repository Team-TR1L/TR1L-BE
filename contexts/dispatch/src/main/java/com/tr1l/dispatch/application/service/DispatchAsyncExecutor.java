package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.DispatchCommand;
import com.tr1l.dispatch.domain.model.vo.DispatchResult;
import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchAsyncExecutor {

    private final DispatchEventPublisher eventPublisher;

    // ExecutorService 생성 (병렬 처리용)
    private final ExecutorService executor =
            Executors.newFixedThreadPool(10); // 필요 시 스레드 수 조정

    public DispatchResult execute(List<DispatchCommand> commands) {

        AtomicInteger messagesCnt = new AtomicInteger();
        AtomicInteger failedMessagesCnt = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();

        for (DispatchCommand command : commands) {

            // ExecutorService에 Runnable 제출
            Future<?> future = executor.submit(() -> {
                try {
                    // Kafka 전송 완료까지 대기
                    eventPublisher.publish(
                            command.userId(),
                            command.billingMonth(),
                            command.channelType(),
                            command.s3Url(),
                            command.destination()
                    );

                    messagesCnt.incrementAndGet();
                } catch (Exception e) {
                    log.warn(
                            "❌ 카프카 메시지 발행 실패 userId: {}",
                            command.userId()
                    );
                    failedMessagesCnt.incrementAndGet();
                }
            });

            futures.add(future);
        }

        // 제출된 모든 작업 완료 대기
        for (Future<?> future : futures) {
            try {
                future.get(); // 모든 메시지 발송 완료까지 블록
            } catch (Exception ignored) {
                // 이미 개별 작업에서 실패 처리했으므로 여기서는 무시
            }
        }

        // 버퍼에 남은 메시지 flush
        eventPublisher.flush();

        return new DispatchResult(
                messagesCnt.get(),
                failedMessagesCnt.get()
        );
    }

    public void shutdown() throws InterruptedException {
        // Executor 종료
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
    }
}
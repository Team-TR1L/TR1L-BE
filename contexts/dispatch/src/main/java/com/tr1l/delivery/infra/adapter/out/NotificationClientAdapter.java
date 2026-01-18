package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.DeliveryResultEventPort;
import com.tr1l.delivery.application.port.out.NotificationClientPort;
import com.tr1l.delivery.domain.DeliveryResultEvent;
import com.tr1l.delivery.infra.strategy.NotificationSender;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
public class NotificationClientAdapter implements NotificationClientPort {

    // NotificationSender를 구현한 모든 빈을 리스트로 주입
    private final List<NotificationSender> senders;
    private final DeliveryResultEventPort eventPort;
    private final Executor notificationExecutor;

    @Override
    public void send(Long userId, String destination, String content, ChannelType channelType) {

        // 메인 스레드가 아닌 별도의 스레드에서 실행
        CompletableFuture.runAsync(() -> {
            boolean isSuccess = false;
            try {
                // 전략 찾기
                NotificationSender sender = senders.stream()
                        .filter(s -> s.supports(channelType))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported channel: " + channelType));

                // 전략 실행
                isSuccess = sender.send(destination, content);

            } catch (Exception e) {
                isSuccess = false;
            } finally {
                // 결과 이벤트 발행
                eventPort.publish(new DeliveryResultEvent(userId, isSuccess));
            }
        }, notificationExecutor);
    }
}
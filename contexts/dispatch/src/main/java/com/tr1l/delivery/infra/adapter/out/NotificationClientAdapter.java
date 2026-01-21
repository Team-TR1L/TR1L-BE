package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.NotificationClientPort;
import com.tr1l.delivery.infra.strategy.NotificationSender;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
public class NotificationClientAdapter implements NotificationClientPort {

    // NotificationSender를 구현한 모든 빈을 리스트로 주입
    private final List<NotificationSender> senders;

    @Override
    public boolean send(String destination, String content, ChannelType channelType) {

        // 메인 스레드가 아닌 별도의 스레드에서 실행
        try {
            NotificationSender sender = senders.stream()
                    .filter(s -> s.supports(channelType))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported channel: " + channelType));

            return sender.send(destination, content); // 여기서 1초 대기 발생
        } catch (Exception e) {
            return false; // 실패 시 false 반환
        }
    }
}
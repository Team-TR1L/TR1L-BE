package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.NotificationClientPort;
import com.tr1l.delivery.infra.strategy.NotificationSender;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
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
    public void send(String destination, String content, ChannelType channelType) {
        NotificationSender sender = senders.stream()
                .filter(s -> s.supports(channelType))
                .findFirst()
                .orElseThrow(() -> new DispatchDomainException(DispatchErrorCode.DELIVERY_FAILED));

        // 여기서 1초 대기 발생
        sender.send(destination, content);
    }
}
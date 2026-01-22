package com.tr1l.delivery.infra.strategy;

import com.tr1l.dispatch.domain.model.enums.ChannelType;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SmsSender implements NotificationSender{
    @Override
    public boolean supports(ChannelType channelType) {
        return ChannelType.SMS == channelType;
    }

    @Override
    public void send(String destination, String content) {
        try {
            // 1초 딜레이
            TimeUnit.SECONDS.sleep(1);

        } catch (InterruptedException e) {
            // sleep에서 에러가 발생해도 그냥 성공처리
            Thread.currentThread().interrupt();
        }
    }
}

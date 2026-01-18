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
    public boolean send(String destination, String content) {
        try {
            // 1초 딜레이
            TimeUnit.SECONDS.sleep(1);

            // SMS은 모두 성공 처리
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}

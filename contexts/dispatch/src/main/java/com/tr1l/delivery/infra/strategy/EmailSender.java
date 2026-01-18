package com.tr1l.delivery.infra.strategy;

import com.tr1l.dispatch.domain.model.enums.ChannelType;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class EmailSender implements NotificationSender {

    @Override
    public boolean supports(ChannelType channelType) {
        return ChannelType.EMAIL == channelType;
    }

    @Override
    public boolean send(String destination, String content) {
        try {
            // 1초 딜레이
            TimeUnit.SECONDS.sleep(1);

            // 1% 확률로 실패 시뮬레이션
            return Math.random() > 0.01;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
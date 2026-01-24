package com.tr1l.delivery.infra.strategy;

import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.error.ErrorCategory;
import com.tr1l.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class EmailSender implements NotificationSender {

    @Override
    public boolean supports(ChannelType channelType) {
        return ChannelType.EMAIL == channelType;
    }

    @Override
    public void send(String destination, String content) {
        try {
            // 1초 딜레이
            TimeUnit.SECONDS.sleep(1);

            // 1% 확률로 실패 시뮬레이션
            if(Math.random() < 0.01){
                throw new DispatchDomainException(DispatchErrorCode.DELIVERY_FAILED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // sleep에서 에러가 발생해도 실패 처리
            throw new DispatchDomainException(DispatchErrorCode.DELIVERY_FAILED);
        }
    }
}
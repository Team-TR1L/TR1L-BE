package com.tr1l.dispatch.application.port.out;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

import java.time.LocalDate;

public interface DispatchEventPublisher {

    void publish(
            Long userId,
            LocalDate billingMonth,
            ChannelType channelType,
            String encryptedS3Url,
            String destination
    );

    void flush();
}
package com.tr1l.dispatch.application.port.out;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

public interface DispatchEventPublisher {

    void publish(
            Long userId,
            Long DispatchPolicyId,
            ChannelType channelType,
            Integer attemptCount,
            String encryptedS3Url,
            String destination
    );
}
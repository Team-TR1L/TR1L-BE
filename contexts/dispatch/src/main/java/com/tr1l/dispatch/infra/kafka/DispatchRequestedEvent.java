package com.tr1l.dispatch.infra.kafka;

import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DispatchRequestedEvent {

    private Long userId;
    private Long dispatchPolicyId;
    private ChannelType channelType;
    private int attemptCount;

    private String encryptedS3Url;
    private String encryptedDestination;
}
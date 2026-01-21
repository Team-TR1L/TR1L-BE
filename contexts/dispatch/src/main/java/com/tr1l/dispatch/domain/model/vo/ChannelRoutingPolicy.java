package com.tr1l.dispatch.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import lombok.Getter;

@Getter
public final class ChannelRoutingPolicy {

    private final ChannelSequence primaryOrder;
    private final int maxAttemptCount;

    @JsonCreator
    public ChannelRoutingPolicy(@JsonProperty("primaryOrder") ChannelSequence primaryOrder) {
        if (primaryOrder == null) {
            throw new DispatchDomainException(DispatchErrorCode.CHANNEL_TYPE_NULL);
        }

        this.primaryOrder = primaryOrder;
        this.maxAttemptCount = primaryOrder.size();
    }
}

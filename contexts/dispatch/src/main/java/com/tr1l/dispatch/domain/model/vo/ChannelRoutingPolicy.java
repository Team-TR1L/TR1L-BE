package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.error.DispatchErrorCode;
import lombok.Getter;

@Getter
public final class ChannelRoutingPolicy {

    private final ChannelSequence primaryOrder;
    private final int maxAttemptCount;

    public ChannelRoutingPolicy(ChannelSequence primaryOrder) {
        if (primaryOrder == null) {
            throw new DispatchDomainException(DispatchErrorCode.CHANNEL_TYPE_NULL);
        }

        this.primaryOrder = primaryOrder;
        this.maxAttemptCount = primaryOrder.size();
    }

    public ChannelSequence primaryOrder() {
        return primaryOrder;
    }

    public int maxAttemptCount() {
        return maxAttemptCount;
    }
}

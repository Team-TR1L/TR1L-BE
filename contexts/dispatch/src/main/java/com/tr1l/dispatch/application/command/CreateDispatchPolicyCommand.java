package com.tr1l.dispatch.application.command;

import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;

public record CreateDispatchPolicyCommand(Long adminId, ChannelRoutingPolicy channelRoutingPolicy) {
    public CreateDispatchPolicyCommand {
        if (adminId == null)
            throw new IllegalArgumentException("adminId is required");

        if (channelRoutingPolicy == null)
            throw new IllegalArgumentException("routingPolicy is required");
    }
}

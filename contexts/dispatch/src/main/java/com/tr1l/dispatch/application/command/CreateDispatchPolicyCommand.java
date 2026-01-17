package com.tr1l.dispatch.application.command;

import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.error.DispatchErrorCode;

public record CreateDispatchPolicyCommand(Long adminId, ChannelRoutingPolicy channelRoutingPolicy) {
    public CreateDispatchPolicyCommand {
        if (adminId == null)
            throw new DispatchDomainException(DispatchErrorCode.ADMIN_ID_NULL);

        if (channelRoutingPolicy == null)
            throw new DispatchDomainException(DispatchErrorCode.ROUTING_POLICY_NULL);
    }
}

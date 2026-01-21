package com.tr1l.apiserver.dto;

import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispatchPolicyRequest {

    private Long adminId;
    private ChannelRoutingPolicy channelRoutingPolicy;
    private String status; // DRAFT, ACTIVE, RETIRED
}
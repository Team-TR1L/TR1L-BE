package com.tr1l.dispatch.infra.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.error.DispatchErrorCode;

public final class ChannelRoutingPolicyJsonConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ChannelRoutingPolicyJsonConverter() {
    }

    // ==================================================
    // Serialize (Domain → JSON)
    // ==================================================
    public static String serialize(ChannelRoutingPolicy policy) {
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new DispatchDomainException(DispatchErrorCode.ROUTING_POLICY_SERIALIZATION_FAILED, e);
        }
    }

    // ==================================================
    // Deserialize (JSON → Domain)
    // ==================================================
    public static ChannelRoutingPolicy deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChannelRoutingPolicy.class);
        } catch (Exception e) {
            throw new DispatchDomainException(DispatchErrorCode.ROUTING_POLICY_DESERIALIZATION_FAILED, e);
        }
    }
}

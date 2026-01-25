package com.tr1l.dispatch.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ChannelRoutingPolicyJsonConverter {

    public static final ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(ChannelRoutingPolicy policy) {
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new DispatchDomainException(
                    DispatchErrorCode.ROUTING_POLICY_SERIALIZATION_FAILED,
                    e
            );
        }
    }

    public static ChannelRoutingPolicy deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChannelRoutingPolicy.class);
        } catch (IOException e) {
            throw new DispatchDomainException(
                    DispatchErrorCode.ROUTING_POLICY_DESERIALIZATION_FAILED,
                    e
            );
        }
    }
}
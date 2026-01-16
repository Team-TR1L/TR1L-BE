package com.tr1l.dispatch.infra.persistence;

import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.ChannelSequence;
import com.tr1l.dispatch.error.DispatchErrorCode;
import com.tr1l.dispatch.infra.converter.ChannelRoutingPolicyJsonConverter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelRoutingPolicyJsonConverterTest {
    @Test
    void serializeAndDeserialize_shouldReturnEquivalentObject() {
        // given
        ChannelSequence seq = new ChannelSequence(List.of(new ChannelType[]{ChannelType.EMAIL, ChannelType.SMS}));
        ChannelRoutingPolicy original = new ChannelRoutingPolicy(seq);

        // when
        String json = ChannelRoutingPolicyJsonConverter.serialize(original);
        ChannelRoutingPolicy restored = ChannelRoutingPolicyJsonConverter.deserialize(json);

        // then
        assertThat(restored).isNotNull();
        assertThat(restored.getPrimaryOrder().channels())
                .isEqualTo(original.primaryOrder().channels());
    }

    @Test
    void serialize_invalidObject_shouldThrowDispatchDomainException() {
        // 여기서는 강제로 직렬화 실패 유도
        ChannelSequence seq = new ChannelSequence(List.of(new ChannelType[]{ChannelType.EMAIL, ChannelType.SMS}));
        ChannelRoutingPolicy badPolicy = new ChannelRoutingPolicy(seq);

        assertThatThrownBy(() -> ChannelRoutingPolicyJsonConverter.serialize(badPolicy))
                .isInstanceOf(DispatchDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.ROUTING_POLICY_SERIALIZATION_FAILED);
    }

    @Test
    void deserialize_invalidJson_shouldThrowDispatchDomainException() {
        String invalidJson = "{ this is not valid json }";

        assertThatThrownBy(() -> ChannelRoutingPolicyJsonConverter.deserialize(invalidJson))
                .isInstanceOf(DispatchDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.ROUTING_POLICY_DESERIALIZATION_FAILED);
    }
}
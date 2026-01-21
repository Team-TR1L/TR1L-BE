package com.tr1l.dispatch.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;
import com.tr1l.dispatch.infra.persistence.converter.ChannelRoutingPolicyJsonConverter;
import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;

import static com.tr1l.dispatch.infra.persistence.converter.ChannelRoutingPolicyJsonConverter.objectMapper;

public final class DispatchPolicyMapper {

    private DispatchPolicyMapper() {}

    // ===============================
    // Entity → Domain
    // ===============================
    public static DispatchPolicy toDomain(DispatchPolicyEntity entity) {
        return DispatchPolicy.restore(
                DispatchPolicyId.of(entity.getId()),
                AdminId.of(entity.getAdminId()),
                entity.getCreatedAt(),
                PolicyStatus.valueOf(entity.getStatus()),
                PolicyVersion.of(Math.max(1, entity.getVersion() + 1)),
                ChannelRoutingPolicyJsonConverter.deserialize(entity.getRoutingPolicyJson()),
                entity.getActivatedAt(),
                entity.getRetiredAt()
        );
    }

    // ===============================
    // Domain → Entity
    // ===============================
    public static DispatchPolicyEntity toEntity(DispatchPolicy policy) throws JsonProcessingException {
        DispatchPolicyEntity entity = new DispatchPolicyEntity();

        // 신규 엔티티일 경우 ID / Version 세팅 금지
        if (policy.getDispatchPolicyId() != null) {
            entity.setId(policy.getDispatchPolicyId().value());
        }


        entity.setAdminId(policy.getAdminId().value());
        entity.setStatus(String.valueOf(policy.getStatus()));
        entity.setRoutingPolicyJson(
                objectMapper.writeValueAsString(policy.getRoutingPolicy())
        );
        entity.setCreatedAt(policy.getCreatedAt());
        entity.setActivatedAt(policy.getActivatedAt());
        entity.setRetiredAt(policy.getRetiredAt());

        return entity;
    }
}
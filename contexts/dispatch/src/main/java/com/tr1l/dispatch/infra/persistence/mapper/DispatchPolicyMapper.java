package com.tr1l.dispatch.infra.persistence.mapper;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;
import com.tr1l.dispatch.infra.persistence.converter.ChannelRoutingPolicyJsonConverter;
import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;

public final class DispatchPolicyMapper {

    private DispatchPolicyMapper() {}

    // ===============================
    // Entity → Domain
    // ===============================
    public static DispatchPolicy toDomain(DispatchPolicyEntity entity) {

        DispatchPolicy policy = new DispatchPolicy(
                DispatchPolicyId.of(entity.getId()),
                AdminId.of(entity.getAdminId()),
                entity.getCreatedAt()
        );

        DispatchPolicy.restore(
                DispatchPolicyId.of(entity.getId()),
                PolicyStatus.valueOf(entity.getStatus()),
                PolicyVersion.of(entity.getVersion()),
                ChannelRoutingPolicyJsonConverter.deserialize(entity.getRoutingPolicyJson()),
                entity.getActivatedAt(),
                entity.getRetiredAt()
        );

        return policy;
    }

    // ===============================
    // Domain → Entity
    // ===============================
    public static DispatchPolicyEntity toEntity(DispatchPolicy domain) {

        return new DispatchPolicyEntity(
                domain.getDispatchPolicyId().value(),
                domain.getAdminId().value(),
                domain.getStatus().name(),
                domain.getVersion().value(),
                ChannelRoutingPolicyJsonConverter.serialize(domain.getRoutingPolicy()),
                domain.getRoutingPolicy().getMaxAttemptCount(),
                domain.getCreatedAt(),
                domain.getActivatedAt(),
                domain.getRetiredAt()
        );
    }
}
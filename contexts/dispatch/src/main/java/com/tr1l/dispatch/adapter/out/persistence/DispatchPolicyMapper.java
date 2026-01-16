package com.tr1l.dispatch.adapter.out.persistence;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;

public final class DispatchPolicyMapper {

    private DispatchPolicyMapper() {
    }

    // ==================================================
    // Domain → Row
    // ==================================================
    public static DispatchPolicyRow toRow(DispatchPolicy policy) {
        return new DispatchPolicyRow(
                policy.getDispatchPolicyId().value(),
                policy.getAdminId().value(),
                policy.getStatus().name(),
                policy.getVersion().value(),
                serialize(policy.getRoutingPolicy()),
                policy.getCreatedAt(),
                policy.getActivatedAt(),
                policy.getRetiredAt()
        );
    }

    // ==================================================
    // Row → Domain
    // ==================================================
    public static DispatchPolicy toDomain(DispatchPolicyRow row) {

        // 1. 영속성 전용 생성자로 최소 상태 생성
        DispatchPolicy policy = new DispatchPolicy(
                DispatchPolicyId.of(row.getId()),
                AdminId.of(row.getAdminId()),
                row.getCreatedAt()
        );

        // 2. 상태 복원 (도메인 규칙 미적용)
        policy.restore(
                PolicyStatus.valueOf(row.getStatus()),
                PolicyVersion.of(row.getVersion()),
                deserialize(row.getRoutingPolicyJson()),
                row.getActivatedAt(),
                row.getRetiredAt()
        );

        return policy;
    }

    // ==================================================
    // Serialization
    // ==================================================
    private static String serialize(ChannelRoutingPolicy policy) {
        if (policy == null) {
            return null;
        }
        return ChannelRoutingPolicyJsonConverter.serialize(policy);
    }

    private static ChannelRoutingPolicy deserialize(String json) {
        if (json == null) {
            return null;
        }
        return ChannelRoutingPolicyJsonConverter.deserialize(json);
    }
}

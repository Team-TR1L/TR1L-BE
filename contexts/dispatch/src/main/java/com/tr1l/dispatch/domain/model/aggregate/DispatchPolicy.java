package com.tr1l.dispatch.domain.model.aggregate;

import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;
import com.tr1l.dispatch.error.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
public class DispatchPolicy {

    private DispatchPolicyId dispatchPolicyId;
    private Instant createdAt;

    // === 가변 상태 ===
    private AdminId adminId;
    private PolicyStatus status;
    private PolicyVersion version;

    private Instant activatedAt;
    private Instant retiredAt;

    private ChannelRoutingPolicy routingPolicy;

    // === 신규 생성 전용 생성자 ===
    public DispatchPolicy(
            DispatchPolicyId dispatchPolicyId,
            AdminId adminId
    ) {
        this.dispatchPolicyId = dispatchPolicyId;
        this.adminId = adminId;
        this.status = PolicyStatus.DRAFT;
        this.version = PolicyVersion.of(1);
        this.createdAt = Instant.now();
    }

    // === 영속성 전용 복원 생성자 ===
    public DispatchPolicy(
            DispatchPolicyId dispatchPolicyId,
            AdminId adminId,
            Instant createdAt
    ) {
        this.dispatchPolicyId = dispatchPolicyId;
        this.adminId = adminId;
        this.createdAt = createdAt;
    }

    // === 영속성 전용 상태 복원 ===
    public static DispatchPolicy restore(
            DispatchPolicyId id,
            AdminId adminId,
            Instant createdAt,
            PolicyStatus status,
            PolicyVersion version,
            ChannelRoutingPolicy routingPolicy,
            Instant activatedAt,
            Instant retiredAt
    ) {
        DispatchPolicy policy = new DispatchPolicy();
        policy.dispatchPolicyId = id;
        policy.adminId = adminId;
        policy.createdAt = createdAt;
        policy.status = status;
        policy.version = version;
        policy.routingPolicy = routingPolicy;
        policy.activatedAt = activatedAt;
        policy.retiredAt = retiredAt;
        return policy;
    }

    // === 신규 생성 ===
    public static DispatchPolicy create(
            AdminId adminId,
            ChannelRoutingPolicy routingPolicy
    ) {
        if (adminId == null)
            throw new DispatchDomainException(DispatchErrorCode.ADMIN_ID_NULL);

        if (routingPolicy == null)
            throw new DispatchDomainException(DispatchErrorCode.ROUTING_POLICY_NULL);

        DispatchPolicy policy = new DispatchPolicy(DispatchPolicyId.generatePolicyId(), adminId);
        policy.routingPolicy = routingPolicy;
        policy.status = PolicyStatus.DRAFT;
        policy.version = PolicyVersion.of(1);
        policy.createdAt = Instant.now();
        return policy;
    }


    // ==================================================
    // 도메인 행위
    // ==================================================

    public void changeRoutingPolicy(
            ChannelRoutingPolicy newRoutingPolicy,
            AdminId actor
    ) {
        if (this.status == PolicyStatus.RETIRED) {
            throw new DispatchDomainException(DispatchErrorCode.POLICY_ALREADY_RETIRED);
        }

        this.routingPolicy = newRoutingPolicy;
        this.adminId = actor;
        this.version = this.version.next();
    }

    public void activate() {
        if (this.status != PolicyStatus.DRAFT) {
            throw new DispatchDomainException(DispatchErrorCode.POLICY_CANNOT_ACTIVATE);
        }

        this.status = PolicyStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    public void retire() {
        if (this.status == PolicyStatus.RETIRED) {
            throw new DispatchDomainException(DispatchErrorCode.POLICY_ALREADY_RETIRED);
        }

        this.status = PolicyStatus.RETIRED;
        this.retiredAt = Instant.now();
    }
}

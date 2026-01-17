package com.tr1l.dispatch.domain.model.aggregate;

import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;
import com.tr1l.dispatch.error.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class DispatchPolicy {

    // === 불변 식별자 ===
    private final DispatchPolicyId dispatchPolicyId;
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
    public void restore(
            PolicyStatus status,
            PolicyVersion version,
            ChannelRoutingPolicy routingPolicy,
            Instant activatedAt,
            Instant retiredAt
    ) {
        this.status = status;
        this.version = version;
        this.routingPolicy = routingPolicy;
        this.activatedAt = activatedAt;
        this.retiredAt = retiredAt;
    }

    public static DispatchPolicy create(
            Long dispatchPolicyId,
            Long adminId,
            ChannelRoutingPolicy routingPolicy
    ) {
        if (dispatchPolicyId == null)
            throw new DispatchDomainException(DispatchErrorCode.DISPATCH_POLICY_ID_NULL);

        if (adminId == null)
            throw new DispatchDomainException(DispatchErrorCode.ADMIN_ID_NULL);

        if (routingPolicy == null)
            throw new DispatchDomainException(DispatchErrorCode.ROUTING_POLICY_NULL);

        DispatchPolicy policy = new DispatchPolicy(
                DispatchPolicyId.of(dispatchPolicyId),
                AdminId.of(adminId)
        );

        policy.initialize(
                new AdminId(adminId),
                routingPolicy
        );

        return policy;
    }

    // ==================================================
    // 초기화 전용 (생성 시 1회)
    // ==================================================

    private void initialize(
            AdminId adminId,
            ChannelRoutingPolicy routingPolicy
    ) {
        this.adminId = adminId;
        this.routingPolicy = routingPolicy;

        this.status = PolicyStatus.DRAFT;
        this.version = PolicyVersion.of(1);

        this.activatedAt = null;
        this.retiredAt = null;
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

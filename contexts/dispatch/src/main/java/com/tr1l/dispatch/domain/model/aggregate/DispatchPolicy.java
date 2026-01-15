package com.tr1l.dispatch.domain.model.aggregate;

import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;
import com.tr1l.dispatch.error.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;

import java.time.Instant;

public class DispatchPolicy {
    // 식별자
    private DispatchPolicyId dispatchPolicyId;

    private AdminId adminId;
    private PolicyStatus status;
    private PolicyVersion version;

    private final Instant createdAt;
    private Instant activatedAt;
    private Instant retiredAt;

    private ChannelRoutingPolicy routingPolicy;

    public DispatchPolicy(AdminId adminId) {
        this.adminId = adminId;
        this.status = PolicyStatus.DRAFT;
        this.version = PolicyVersion.of(1);
        this.createdAt = Instant.now();
    }

    // === 도메인 행위 ===

    public void changeRoutingPolicy(
            ChannelRoutingPolicy newRoutingPolicy,
            AdminId actor
    ) {
        if (this.status == PolicyStatus.RETIRED)
            throw new DispatchDomainException(DispatchErrorCode.POLICY_ALREADY_RETIRED);

        this.routingPolicy = newRoutingPolicy;
        this.adminId = actor;
        this.version = this.version.next();
    }

    //정책 활성화
    public void activate() {
        if (this.status != PolicyStatus.DRAFT) {
            throw new DispatchDomainException(DispatchErrorCode.POLICY_CANNOT_ACTIVATE);
        }

        this.status = PolicyStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    //정책 폐기
    public void retire() {
        if (this.status == PolicyStatus.RETIRED) {
            throw new DispatchDomainException(DispatchErrorCode.POLICY_ALREADY_RETIRED);
        }

        this.status = PolicyStatus.RETIRED;
        this.retiredAt = Instant.now();
    }

    // === 식별자 세터 (영속화 시점) ===
    public void assignId(Long id) {
        this.dispatchPolicyId = DispatchPolicyId.of(id);
    }
}
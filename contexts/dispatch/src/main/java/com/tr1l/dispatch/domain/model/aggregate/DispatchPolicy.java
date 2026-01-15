package com.tr1l.dispatch.domain.model.aggregate;

import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;

import java.time.Instant;

public class DispatchPolicy {
    //식별자
    private final DispatchPolicyId dispatchPolicyId;

    private AdminId adminId; // 생성자 혹은 관리자
    private PolicyStatus status;
    private PolicyVersion version;

    private final Instant createdAt;
    private Instant activateAt;
    private Instant retiredAt;

    // 연관 관계 (VO)
    private ChannelRoutingPolicy routingPolicy;

    public DispatchPolicy() {
        this.dispatchPolicyId = DispatchPolicyId.generatePolicyId();
        this.createdAt = Instant.now();
    }

    //라우팅 정책을 변경하고, 버전을 올리거나 수정자 정보를 갱신합니다.
    public void changeRoutingPolicy(ChannelRoutingPolicy newRoutingPolicy, AdminId actor) {
        if (this.status == PolicyStatus.RETIRED) {
            //TODO: 에러 코드 변경
            throw new IllegalStateException("Cannot modify a retired policy.");
        }

        this.routingPolicy = newRoutingPolicy;
        this.adminId = actor;
        this.version = this.version.next();
    }

    // 정책 활성화
    public void activate() {
        this.status = PolicyStatus.ACTIVE;
        this.activateAt = Instant.now();
    }

    // 정책 폐기
    public void retire() {
        this.status = PolicyStatus.RETIRED;
        this.retiredAt = Instant.now();
    }
}

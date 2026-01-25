package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.infra.persistence.repository.DispatchPolicyRepository;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DispatchPolicyService {

    private final DispatchPolicyRepository repository;

    public Long createPolicy(CreateDispatchPolicyCommand command) {
        DispatchPolicy policy = DispatchPolicy.create(
                AdminId.of(command.adminId()),
                command.channelRoutingPolicy()
        );

        DispatchPolicy saved = repository.save(policy);
        return saved.getDispatchPolicyId().value();
    }

    @Transactional(readOnly = true)
    public List<DispatchPolicy> findPolicies() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public DispatchPolicy findPolicy(Long policyId) {
        return repository.findById(policyId)
                .orElseThrow(() -> new DispatchDomainException(DispatchErrorCode.POLICY_NOT_FOUND));
    }

    public void deletePolicy(Long policyId) {
        DispatchPolicy policy = findPolicy(policyId);
        if (policy.getStatus() == PolicyStatus.ACTIVE) {
            throw new DispatchDomainException(DispatchErrorCode.ACTIVATE_POLICY_NOT_RETIRED);
        }

        policy.retire();          // ← 도메인 규칙
        repository.save(policy);
    }


    /**
     * 정책 활성화 로직
     * 1. 기존에 ACTIVE인 정책이 있다면 DRAFT(또는 RETIRE)로 변경 (단일성 보장)
     * 2. 대상 정책을 ACTIVE로 변경
     */
    public void activatePolicy(Long policyId) {
        // 1. 기존 활성 정책이 있는지 확인 (있을 때만 처리, 없어도 에러 X)
        repository.findCurrentPolicy().ifPresent(activePolicy -> {
            activePolicy.draft(); // 혹은 상황에 따라 retire()
            repository.save(activePolicy);
        });

        // 2. 새로운 정책 활성화
        DispatchPolicy policy = findPolicy(policyId);
        policy.activate();
        repository.save(policy);
    }

    // 현재 활성 정책 조회
    @Transactional(readOnly = true)
    public DispatchPolicy findCurrentActivePolicy(){
        return repository.findCurrentPolicy()
                .orElseThrow(() -> new DispatchDomainException(DispatchErrorCode.ACTIVE_POLICY_NOT_FOUND));
    }

     //정책 수정 및 상태 변경
    @Transactional
    public DispatchPolicy updatePolicy(Long policyId, ChannelRoutingPolicy newRoutingPolicyJson, String newStatus) {
        DispatchPolicy policy = findPolicy(policyId);
        PolicyStatus targetStatus = PolicyStatus.valueOf(newStatus);

        if (targetStatus == PolicyStatus.ACTIVE && policy.getStatus() != PolicyStatus.ACTIVE) {
            activatePolicy(policyId);
            return findPolicy(policyId);
        }

        // 일반 수정 로직
        policy.changeRoutingPolicy(newRoutingPolicyJson, policy.getAdminId());
        policy.setStatus(targetStatus);
        policy.incrementVersion();

        return repository.save(policy);
    }
}
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
        policy.retire();          // ← 도메인 규칙
        repository.save(policy);
    }

    @Transactional(readOnly = true)
    public DispatchPolicy findCurrentActivePolicy(){
        return repository.findCurrentPolicy()
                .orElseThrow(() -> new DispatchDomainException(DispatchErrorCode.ACTIVE_POLICY_NOT_FOUND));
    }

    @Transactional
    public DispatchPolicy updatePolicy(Long policyId, ChannelRoutingPolicy newRoutingPolicyJson, String newStatus) {
        DispatchPolicy policy = findPolicy(policyId);

        // ACTIVE 단일성 처리
        if ("ACTIVE".equals(newStatus) && !"ACTIVE".equals(policy.getStatus())) {
            repository.findCurrentPolicy().ifPresent(activePolicy -> {
                activePolicy.retire(); // 기존 ACTIVE 정책 RETIRED 처리
                repository.save(activePolicy);
            });
            policy.activate(); // 현재 정책 ACTIVE 처리
        }

        // 채널 정책 수정
        policy.changeRoutingPolicy(newRoutingPolicyJson, AdminId.of(0L)); // 하드코딩

        // 상태 변경 (DRAFT, RETIRED 등)
        policy.setStatus(PolicyStatus.valueOf(newStatus));

        // 버전 증가
        policy.incrementVersion();

        return repository.save(policy);
    }

}
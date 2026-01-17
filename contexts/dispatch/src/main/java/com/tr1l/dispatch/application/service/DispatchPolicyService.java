package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.port.DispatchPolicyRepository;
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
}
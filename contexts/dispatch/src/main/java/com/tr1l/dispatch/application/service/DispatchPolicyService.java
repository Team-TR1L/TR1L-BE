package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.port.DispatchPolicyRepository;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DispatchPolicyService {

    private final DispatchPolicyRepository repository;

    public Long createPolicy(CreateDispatchPolicyCommand command) {
        DispatchPolicy policy = DispatchPolicy.create(
                DispatchPolicyId.generatePolicyId().value(),
                command.adminId(),
                command.channelRoutingPolicy()
        );

        repository.save(policy);
        return policy.getDispatchPolicyId().value();
    }

    public List<DispatchPolicy> findPolicies() {
        return repository.findAll();
    }

    public DispatchPolicy findPolicy(Long policyId) {
        return repository.findById(policyId)
                .orElseThrow(() -> new RuntimeException(String.valueOf(policyId)));
    }

    public void deletePolicy(Long policyId) {
        DispatchPolicy policy = findPolicy(policyId);
        policy.retire();          // ← 도메인 규칙
        repository.save(policy);
    }
}
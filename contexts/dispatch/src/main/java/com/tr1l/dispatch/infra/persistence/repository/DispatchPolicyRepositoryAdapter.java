package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.application.port.DispatchPolicyRepository;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.infra.persistence.mapper.DispatchPolicyMapper;
import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DispatchPolicyRepositoryAdapter
        implements DispatchPolicyRepository {

    private final DispatchPolicyJpaRepository jpaRepository;

    @Override
    public void save(DispatchPolicy policy) {
        DispatchPolicyEntity entity = DispatchPolicyMapper.toEntity(policy);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<DispatchPolicy> findById(Long id) {
        return jpaRepository.findById(id)
                .map(DispatchPolicyMapper::toDomain);
    }
}
package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.infra.persistence.mapper.DispatchPolicyMapper;
import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DispatchPolicyRepositoryAdapter
        implements DispatchPolicyRepository {

    private final DispatchPolicyJpaRepository jpaRepository;

    @Override
    public DispatchPolicy save(DispatchPolicy policy) {

        DispatchPolicyEntity entity =
                DispatchPolicyMapper.toEntity(policy);

        DispatchPolicyEntity saved =
                jpaRepository.save(entity);

        return DispatchPolicyMapper.toDomain(saved);
    }

    @Override
    public Optional<DispatchPolicy> findById(Long id) {
        return jpaRepository.findById(id)
                .map(DispatchPolicyMapper::toDomain);
    }

    @Override
    public List<DispatchPolicy> findAll() {
        return jpaRepository.findAll()
                .stream().map(DispatchPolicyMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DispatchPolicy> findCurrentPolicy() {
        return jpaRepository.findCurrentPolicy()
                .map(DispatchPolicyMapper::toDomain);
    }
}
package com.tr1l.dispatch.application.port;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;

import java.util.List;
import java.util.Optional;

public interface DispatchPolicyRepository {
    DispatchPolicy save(DispatchPolicy policy);

    Optional<DispatchPolicy> findById(Long id);

    List<DispatchPolicy> findAll();

    Optional<DispatchPolicy> findCurrentPolicy();
}
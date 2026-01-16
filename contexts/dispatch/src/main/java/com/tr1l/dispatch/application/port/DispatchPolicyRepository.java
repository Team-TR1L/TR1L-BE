package com.tr1l.dispatch.application.port;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import java.util.Optional;

public interface DispatchPolicyRepository {
    void save(DispatchPolicy policy);

    Optional<DispatchPolicy> findById(Long id);
}
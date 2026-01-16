package com.tr1l.dispatch.application.port.out;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;

public interface DispatchPolicyRepositoryPort {
    void save(DispatchPolicy policy);
}

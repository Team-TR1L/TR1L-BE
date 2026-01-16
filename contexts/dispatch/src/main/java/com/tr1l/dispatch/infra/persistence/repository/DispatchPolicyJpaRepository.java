package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchPolicyJpaRepository
        extends JpaRepository<DispatchPolicyEntity, Long> {
}
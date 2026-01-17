package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.DispatchPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DispatchPolicyJpaRepository
        extends JpaRepository<DispatchPolicyEntity, Long> {

    @Query("""
        select p
        from DispatchPolicyEntity p
        where p.status = 'ACTIVE'
          and p.retiredAt is null
    """)
    Optional<DispatchPolicyEntity> findCurrentPolicy();
}
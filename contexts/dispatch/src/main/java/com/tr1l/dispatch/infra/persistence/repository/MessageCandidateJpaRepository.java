package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageCandidateJpaRepository
        extends JpaRepository<BillingTargetEntity, Long> {

    @Query("""
    select c
    from BillingTargetEntity c
    where c.sendStatus in ('READY', 'FAILED')
      and (
            :currentHour < cast(c.fromTime as integer)
         or :currentHour >= cast(c.toTime as integer)
      )
""")
    List<BillingTargetEntity> findReadyCandidates(
            @Param("currentHour") Integer currentHour,
            Pageable pageable
    );
}


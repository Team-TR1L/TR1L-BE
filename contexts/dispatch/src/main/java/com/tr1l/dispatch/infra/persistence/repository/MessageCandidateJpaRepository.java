package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MessageCandidateJpaRepository
        extends JpaRepository<BillingTargetEntity, BillingTargetId> {

    @Query("""
        select c
        from BillingTargetEntity c
        where c.id.userId > :lastUserId
          and c.id.billingMonth = :billingMonth
          and (c.dayTime = :dayTime or c.dayTime is null)
          and c.sendStatus in ('READY', 'FAILED')
          and c.attemptCount <= :maxAttemptCount
          and (
              (c.fromTime is null and c.toTime is null)
              or (
                  :currentHour < cast(c.fromTime as integer)
                  or :currentHour >= cast(c.toTime as integer)
              )
          )
        order by c.id.userId asc
    """)
    List<BillingTargetEntity> findReadyCandidatesByUserCursor(
            @Param("billingMonth") LocalDate billingMonth,
            @Param("lastUserId") Long lastUserId,
            @Param("dayTime") String dayTime,
            @Param("maxAttemptCount") Integer maxAttemptCount,
            @Param("currentHour") Integer currentHour,
            Pageable pageable
    );
}

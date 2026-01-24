package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.*;
import java.time.LocalDate;
import java.util.List;


@Repository
public interface MessageCandidateJpaRepository
        extends JpaRepository<BillingTargetEntity, BillingTargetId> {

    @Query(value = """
    SELECT *
    FROM billing_targets
    WHERE user_id > :lastUserId
      AND billing_month = :billingMonth
      AND (day_time = :dayTime OR day_time IS NULL)
      AND send_status IN ('READY','FAILED')
      AND attempt_count <= :maxAttemptCount
      AND ((from_time IS NULL AND to_time IS NULL)
           OR (:currentHour < CAST(from_time AS INTEGER) OR :currentHour >= CAST(to_time AS INTEGER)))
    ORDER BY user_id ASC
    FOR UPDATE SKIP LOCKED
    LIMIT :pageSize
    """, nativeQuery = true)

    List<BillingTargetEntity> findReadyCandidatesByUserCursorNative(
            @Param("billingMonth") LocalDate billingMonth,
            @Param("lastUserId") Long lastUserId,
            @Param("dayTime") String dayTime,
            @Param("maxAttemptCount") Integer maxAttemptCount,
            @Param("currentHour") Integer currentHour,
            @Param("pageSize") int pageSize
    );
}


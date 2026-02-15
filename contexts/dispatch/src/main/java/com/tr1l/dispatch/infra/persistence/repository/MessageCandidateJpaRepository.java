package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.domain.dto.AttemptChannelCount;
import com.tr1l.dispatch.domain.dto.BillingDailyCount;
import com.tr1l.dispatch.domain.dto.BillingResultCount;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;


@Repository
public interface MessageCandidateJpaRepository
        extends JpaRepository<BillingTargetEntity, BillingTargetId> {

    @Query(value = """
    SELECT *
    FROM billing_targets
    WHERE user_id > :lastUserId
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
            @Param("lastUserId") Long lastUserId,
            @Param("dayTime") String dayTime,
            @Param("maxAttemptCount") Integer maxAttemptCount,
            @Param("currentHour") Integer currentHour,
            @Param("pageSize") int pageSize
    );

    //1. 일별 전송량 집계 (billingMonth + dayTime)
    @Query("""
        select new com.tr1l.dispatch.domain.dto.BillingDailyCount(
            m.id.billingMonth,
            m.dayTime,
            count(m)
        )
        from BillingTargetEntity m
        where m.id.billingMonth in :billingMonths
          and m.dayTime between :startDay and :endDay
        group by m.id.billingMonth, m.dayTime
    """)
    List<BillingDailyCount> countByBillingMonthAndDayTime(
            @Param("billingMonths") Set<LocalDate> billingMonths,
            @Param("startDay") String startDay,
            @Param("endDay") String endDay
    );

    //2. 채널 분포 집계 (attemptCount 기준)
    @Query("""
        select new com.tr1l.dispatch.domain.dto.AttemptChannelCount(
            m.attemptCount,
            count(m)
        )
        from BillingTargetEntity m
        where m.attemptCount in :attemptCounts
          and m.id.billingMonth in :billingMonths
        group by m.attemptCount
    """)
    List<AttemptChannelCount> countByAttemptCountAndBillingMonth(
            @Param("attemptCounts") List<Integer> attemptCounts,
            @Param("billingMonths") Set<LocalDate> billingMonths
    );

    //3. 오늘 성공 / 실패 집계
    @Query("""
        select new com.tr1l.dispatch.domain.dto.BillingResultCount(
            sum(case when m.sendStatus = 'SUCCESS' then 1 else 0 end),
            sum(case when m.sendStatus = 'FAIL' then 1 else 0 end)
        )
        from BillingTargetEntity m
        where m.id.billingMonth = :billingMonth
          and m.dayTime = :dayTime
    """)
    BillingResultCount countTodayResult(
            @Param("billingMonth") LocalDate billingMonth,
            @Param("dayTime") String dayTime
    );
}
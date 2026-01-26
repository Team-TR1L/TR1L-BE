package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.dto.*;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.infra.persistence.repository.DispatchPolicyRepository;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class DispatchPolicyService {

    private final DispatchPolicyRepository repository;
    private final MessageCandidateJpaRepository messageRepository;

    public Long createPolicy(CreateDispatchPolicyCommand command) {
        DispatchPolicy policy = DispatchPolicy.create(
                AdminId.of(command.adminId()),
                command.channelRoutingPolicy()
        );

        DispatchPolicy saved = repository.save(policy);
        return saved.getDispatchPolicyId().value();
    }

    @Transactional(readOnly = true)
    public List<DispatchPolicy> findPolicies() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public DispatchPolicy findPolicy(Long policyId) {
        return repository.findById(policyId)
                .orElseThrow(() -> new DispatchDomainException(DispatchErrorCode.POLICY_NOT_FOUND));
    }

    public void deletePolicy(Long policyId) {
        DispatchPolicy policy = findPolicy(policyId);
        if (policy.getStatus() == PolicyStatus.ACTIVE) {
            throw new DispatchDomainException(DispatchErrorCode.ACTIVATE_POLICY_NOT_RETIRED);
        }

        policy.retire();          // ← 도메인 규칙
        repository.save(policy);
    }


    /**
     * 정책 활성화 로직
     * 1. 기존에 ACTIVE인 정책이 있다면 DRAFT(또는 RETIRE)로 변경 (단일성 보장)
     * 2. 대상 정책을 ACTIVE로 변경
     */
    public void activatePolicy(Long policyId) {
        // 1. 기존 활성 정책이 있는지 확인 (있을 때만 처리, 없어도 에러 X)
        repository.findCurrentPolicy().ifPresent(activePolicy -> {
            activePolicy.draft(); // 혹은 상황에 따라 retire()
            repository.save(activePolicy);
        });

        // 2. 새로운 정책 활성화
        DispatchPolicy policy = findPolicy(policyId);
        policy.activate();
        repository.save(policy);
    }

    // 현재 활성 정책 조회
    @Transactional(readOnly = true)
    public DispatchPolicy findCurrentActivePolicy(){
        return repository.findCurrentPolicy()
                .orElseThrow(() -> new DispatchDomainException(DispatchErrorCode.ACTIVE_POLICY_NOT_FOUND));
    }

     //정책 수정 및 상태 변경
    @Transactional
    public DispatchPolicy updatePolicy(Long policyId, ChannelRoutingPolicy newRoutingPolicyJson, String newStatus) {
        DispatchPolicy policy = findPolicy(policyId);
        PolicyStatus targetStatus = PolicyStatus.valueOf(newStatus);

        if (targetStatus == PolicyStatus.ACTIVE && policy.getStatus() != PolicyStatus.ACTIVE) {
            activatePolicy(policyId);
            return findPolicy(policyId);
        }

        // 일반 수정 로직
        policy.changeRoutingPolicy(newRoutingPolicyJson, policy.getAdminId());
        policy.setStatus(targetStatus);
        policy.incrementVersion();

        return repository.save(policy);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {

        DispatchPolicy policy = findCurrentActivePolicy();
        List<ChannelType> channels =
                policy.getRoutingPolicy().getPrimaryOrder().channels();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);

    /* =========================
       1. billingMonth 계산
       ========================= */
        Set<LocalDate> billingMonths = Stream.of(startDate, today)
                .map(d -> d.withDayOfMonth(1))
                .collect(Collectors.toSet());

    /* =========================
       2. 일별 전송량 (Billing 기준)
       ========================= */
        String startDay = String.format("%02d", startDate.getDayOfMonth());
        String endDay   = String.format("%02d", today.getDayOfMonth());

        List<BillingDailyCount> rawDailyCounts =
                messageRepository.countByBillingMonthAndDayTime(
                        billingMonths,
                        startDay,
                        endDay
                );

        Map<LocalDate, Long> dailyCountMap = new HashMap<>();

        for (BillingDailyCount row : rawDailyCounts) {
            LocalDate actualDate = row.billingMonth()
                    .withDayOfMonth(Integer.parseInt(row.dayTime()));
            dailyCountMap.put(actualDate, row.count());
        }

        List<DailyTrendDto> dailyTrend = IntStream.rangeClosed(0, 6)
                .mapToObj(i -> {
                    LocalDate date = startDate.plusDays(i);
                    return DailyTrendDto.builder()
                            .date(date.getMonthValue() + "/" + date.getDayOfMonth())
                            .sent(dailyCountMap.getOrDefault(date, 0L))
                            .build();
                })
                .toList();

        long todaySent = dailyCountMap.getOrDefault(today, 0L);

    /* =========================
       3. 채널 분포 (attemptCount 기반)
       ========================= */
        List<Integer> attemptCounts =
                IntStream.range(0, channels.size())
                        .boxed()
                        .toList();

        List<AttemptChannelCount> rawCounts =
                messageRepository.countByAttemptCountAndBillingMonth(
                        attemptCounts,
                        billingMonths
                );

        Map<Integer, Long> attemptCountMap =
                rawCounts.stream()
                        .collect(Collectors.toMap(
                                AttemptChannelCount::attemptCount,
                                AttemptChannelCount::count
                        ));

        Map<ChannelType, Long> channelCountMap = new EnumMap<>(ChannelType.class);

        for (int i = 0; i < channels.size(); i++) {
            ChannelType channel = channels.get(i);
            long count = attemptCountMap.getOrDefault(i, 0L);
            channelCountMap.put(channel, count);
        }

        long totalChannelSent = channelCountMap.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        List<ChannelDistributionDto> channelDistribution =
                channels.stream()
                        .map(channel -> {
                            long count = channelCountMap.getOrDefault(channel, 0L);
                            int percent = totalChannelSent == 0
                                    ? 0
                                    : (int) Math.round(count * 100.0 / totalChannelSent);

                            return ChannelDistributionDto.builder()
                                    .name(channel.name())
                                    .value(percent)
                                    .build();
                        })
                        .toList();

    /* =========================
       4. 오늘 성공 / 실패율
       ========================= */
        LocalDate billingMonth = today.withDayOfMonth(1);
        String todayDayTime = String.format("%02d", today.getDayOfMonth());

        BillingResultCount resultCount =
                messageRepository.countTodayResult(billingMonth, todayDayTime);

        long success = resultCount == null || resultCount.success() == null
                ? 0 : resultCount.success();
        long failure = resultCount == null || resultCount.failure() == null
                ? 0 : resultCount.failure();

        long totalToday = success + failure;

        double successRate = totalToday == 0
                ? 0.0
                : Math.round((success * 1000.0) / totalToday) / 10.0;

        double failureRate = totalToday == 0
                ? 0.0
                : Math.round((failure * 1000.0) / totalToday) / 10.0;

    /* =========================
       5. 응답 DTO
       ========================= */
        return DashboardStatsDto.builder()
                .todaySent(todaySent)
                .successRate(successRate)
                .failureRate(failureRate)
                .dailyTrend(dailyTrend)
                .channelDistribution(channelDistribution)
                .build();
    }

}
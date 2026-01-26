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
        LocalDate today = LocalDate.now();

    /* =========================
       1. 일별 추이 (4일 하드코딩 + 오늘 발송량)
       ========================= */
            List<DailyTrendDto> dailyTrend = new ArrayList<>();
            dailyTrend.add(new DailyTrendDto(today.minusDays(4).getMonthValue() + "/" + today.minusDays(4).getDayOfMonth(), 1250L));
            dailyTrend.add(new DailyTrendDto(today.minusDays(3).getMonthValue() + "/" + today.minusDays(3).getDayOfMonth(), 2100L));
            dailyTrend.add(new DailyTrendDto(today.minusDays(2).getMonthValue() + "/" + today.minusDays(2).getDayOfMonth(), 1850L));
            dailyTrend.add(new DailyTrendDto(today.minusDays(1).getMonthValue() + "/" + today.minusDays(1).getDayOfMonth(), 2400L));

            // 오늘 발송 총합: 400(8시)+ 600(10시) + 850(12시) = 1850
            long todaySent = 1850L;
            // 실제 오늘 14시에 발송할 메시지 수 조회
            BillingResultCount todaySent14 = messageRepository.countTodayResult(LocalDate.parse("2025-12-01"), "26");
            long today14Total = todaySent14.failure() + todaySent14.success();
            dailyTrend.add(new DailyTrendDto(today.getMonthValue() + "/" + today.getDayOfMonth(), todaySent));

    /* =========================
       2. 시간별 추이 (8시, 10시, 12시 고정 + 14시는 실제 값 가져오기)
       ========================= */
            List<HourlyTrendDto> hourlyTrend = List.of(
                    HourlyTrendDto.builder().hour("08").count(400L).build(),
                    HourlyTrendDto.builder().hour("10").count(600L).build(),
                    HourlyTrendDto.builder().hour("12").count(850L).build(),
                    HourlyTrendDto.builder().hour("14").count(today14Total).build()
            );

    /* =========================
       3. 채널 분포 및 성공/실패율 (실제 수량 기반 분배)
       ======================== */
            // 실패율 약 1% 적용: 1850 * 0.01 = 18.5 -> 반올림하여 19건
            long failureCount = Math.round((todaySent + today14Total) * 0.01);
            long successCount =  (todaySent + today14Total) - failureCount; // 나머지는 성공

            // 성공/실패율 계산 (소수점 첫째 자리까지)
            double successRate = todaySent == 0 ? 0.0 : Math.round((successCount * 1000.0) / todaySent) / 10.0;
            double failureRate = todaySent == 0 ? 0.0 : Math.round((failureCount * 1000.0) / todaySent) / 10.0;

    /* =========================
       4. 채널별 전송 수량 할당 (EMAIL: 성공, SMS: 실패)
       ========================= */
            List<ChannelDistributionDto> channelDistribution = List.of(
                    // 퍼센트가 아닌 실제 '개수'를 value에 할당
                    new ChannelDistributionDto("EMAIL", (int) successCount),
                    new ChannelDistributionDto("SMS", (int) failureCount)
            );

    /* =========================
       5. 응답 DTO 반환
       ========================= */
            return DashboardStatsDto.builder()
                    .todaySent(todaySent)
                    .successRate(successRate)
                    .failureRate(failureRate)
                    .dailyTrend(dailyTrend)
                    .channelDistribution(channelDistribution)
                    .hourlyTrend(hourlyTrend)
                    .build();
        }
}
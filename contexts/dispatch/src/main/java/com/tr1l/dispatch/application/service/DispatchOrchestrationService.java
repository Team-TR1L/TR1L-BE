package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.port.in.DispatchOrchestrationUseCase;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import com.tr1l.dispatch.infra.s3.S3LocationMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOrchestrationService implements DispatchOrchestrationUseCase {

    private final MessageCandidateJpaRepository candidateRepository;
    private final DispatchPolicyService dispatchPolicyService;
    private final DispatchEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final S3LocationMapper s3LocationMapper;


    @Transactional
    public void orchestrate(Instant now) throws InterruptedException {
        Instant startTime = Instant.now();
        log.warn("🕒 Step 0: 오케스트레이션 시작 - {}", startTime);

        // 1. 발송 정책 조회
        log.warn("🔍 Step 1: 활성 발송 정책 조회 중...");
        DispatchPolicy policy = dispatchPolicyService.findCurrentActivePolicy();

        List<ChannelType> channels =
                policy.getRoutingPolicy().getPrimaryOrder().channels();

        // 2. 기준 시간 계산 (now 기준으로 통일)
        LocalDateTime nowKst = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"));
        int currentHour = nowKst.getHour();
        String dayTime = String.format("%02d", nowKst.getDayOfMonth());
        LocalDate billingMonth = nowKst.toLocalDate().withDayOfMonth(1);

        // 3. Cursor 초기화
        Long lastUserId = 0L;
        int pageSize = 1000;

        // 4. 카프카에 발행할 메시지 개수 카운터
        int candidatesCnt = 0;
        AtomicInteger messagesCnt = new AtomicInteger();
        AtomicInteger failedMessagesCnt = new AtomicInteger();

        // 5. Cursor 기반 배치 조회 (✅ 동시 실행 시 Cursor 충돌 가능 문제 해결)
        log.warn("📦 Step 2: 후보 배치 처리 시작...");
        // ExecutorService 생성 (병렬 처리용)
        ExecutorService executor = Executors.newFixedThreadPool(10); // 필요 시 스레드 수 조정

        while (true) {
            List<BillingTargetEntity> candidates =
                    candidateRepository.findReadyCandidatesByUserCursorNative(
                            billingMonth,
                            lastUserId,
                            dayTime,
                            channels.size() - 1,
                            currentHour,
                            pageSize
                    );

            if (candidates.isEmpty()) {
                log.warn("✅ 더 이상 후보가 없습니다. 배치 처리 종료.");
                break;
            }

            candidatesCnt += candidates.size();
            List<Future<?>> futures = new ArrayList<>();

            for (BillingTargetEntity candidate : candidates) {
                ChannelType nowChannel = channels.get(
                        Math.min(channels.size() - 1, candidate.getAttemptCount())
                );

                String s3url = s3LocationMapper.extractLocationValueByChannel(candidate.getS3UrlJsonb(), nowChannel);
                String destination = s3LocationMapper.extractValueByChannel(candidate.getSendOptionJsonb(), nowChannel);

                // ExecutorService에 Runnable 제출
                Future<?> future = executor.submit(() -> {
                    try {
                        // Kafka 전송 완료까지 대기
                        eventPublisher.publish(
                                candidate.getId().getUserId(),
                                candidate.getId().getBillingMonth(),
                                nowChannel, s3url, destination);

                        messagesCnt.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("❌ 카프카 메시지 발행 실패 userId: {}", candidate.getId().getUserId());

                        candidate.setSendStatus("FAILED");
                        candidateRepository.save(candidate);

                        failedMessagesCnt.getAndIncrement();
                    }
                });

                futures.add(future);

                // Cursor 이동
                lastUserId = candidate.getId().getUserId();
            }

            // 제출된 모든 작업 완료 대기
            for (Future<?> future : futures) {
                try {
                    future.get(); // 모든 메시지 발송 완료까지 블록
                } catch (Exception ignored) {
                    // 이미 개별 작업에서 실패 처리했으므로 여기서는 무시
                }
            }

            // 버퍼에 남은 메시지 flush
            eventPublisher.flush();

            // 7. 영속성 컨텍스트 정리 (OOM 방지)
            entityManager.clear();
        }
        // Executor 종료
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        log.warn("🏁 Step 3: 오케스트레이션 완료. 총 후보: {}, 총 발행 메시지 수: {}, 총 발행 실패 메시지수: {}",
                candidatesCnt, messagesCnt, failedMessagesCnt);

        Instant endTime = Instant.now();
        log.warn("✅ 오케스트레이션 시작: {}, 종료: {}, 소요 시간(ms): {}"
                , startTime, endTime, Duration.between(startTime, endTime).toMillis());
    }
}

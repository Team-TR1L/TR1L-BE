package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.domain.model.vo.BatchResult;
import com.tr1l.dispatch.domain.model.vo.DispatchResult;
import com.tr1l.dispatch.application.port.in.DispatchOrchestrationUseCase;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOrchestrationService implements DispatchOrchestrationUseCase {

    private final DispatchPolicyService dispatchPolicyService;
    private final CandidateBatchService batchService;
    private final DispatchAsyncExecutor asyncExecutor;

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
        AtomicInteger messagesCnt = new AtomicInteger();
        AtomicInteger failedMessagesCnt = new AtomicInteger();
        int candidatesCnt = 0;

        // 5. Cursor 기반 배치 조회 (✅ 동시 실행 시 Cursor 충돌 가능 문제 해결)
        log.warn("📦 Step 2: 후보 배치 처리 시작...");

        while (true) {

            BatchResult batch = batchService.loadAndPrepareBatch(
                    policy,
                    billingMonth,
                    dayTime,
                    currentHour,
                    lastUserId,
                    pageSize
            );

            if (batch.isEmpty()) {
                log.warn("✅ 더 이상 후보가 없습니다. 배치 처리 종료.");
                break;
            }

            candidatesCnt += batch.commands().size();

            DispatchResult result =
                    asyncExecutor.execute(batch.commands());

            messagesCnt.addAndGet(result.success());
            failedMessagesCnt.addAndGet(result.failed());

            lastUserId = batch.lastUserId();
        }

        asyncExecutor.shutdown();

        log.warn(
                "🏁 Step 3: 오케스트레이션 완료. 총 후보: {}, 총 발행 메시지 수: {}, 총 발행 실패 메시지수: {}",
                candidatesCnt, messagesCnt, failedMessagesCnt
        );

        Instant endTime = Instant.now();
        log.warn(
                "✅ 오케스트레이션 시작: {}, 종료: {}, 소요 시간(ms): {}",
                startTime, endTime, Duration.between(startTime, endTime).toMillis()
        );
    }
}
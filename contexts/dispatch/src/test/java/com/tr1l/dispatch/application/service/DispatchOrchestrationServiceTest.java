package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.vo.BatchResult;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.ChannelSequence;
import com.tr1l.dispatch.domain.model.vo.DispatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchOrchestrationServiceTest {

    @InjectMocks
    private DispatchOrchestrationService orchestrationService;

    @Mock
    private DispatchPolicyService dispatchPolicyService;

    @Mock
    private CandidateBatchService batchService;

    @Mock
    private DispatchAsyncExecutor asyncExecutor;

    /**
     * 정상 시나리오
     * - batch 2회 처리 후 empty 반환
     * - execute() 2회 호출
     * - shutdown() 호출
     */
    @Test
    void orchestrate_successFlow() throws InterruptedException {

        // given
        DispatchPolicy policy = mockPolicy();
        when(dispatchPolicyService.findCurrentActivePolicy())
                .thenReturn(policy);

        // 첫 번째 batch
        BatchResult batch1 = new BatchResult(
                List.of(mockCommand(), mockCommand()),
                2L
        );

        // 두 번째 batch
        BatchResult batch2 = new BatchResult(
                List.of(mockCommand()),
                3L
        );

        // 종료 batch
        BatchResult emptyBatch = BatchResult.empty(3L);

        when(batchService.loadAndPrepareBatch(
                any(), anyString(), anyInt(), anyLong(), anyInt()
        )).thenReturn(batch1, batch2, emptyBatch);

        when(asyncExecutor.execute(batch1.commands()))
                .thenReturn(new DispatchResult(2, 0));
        when(asyncExecutor.execute(batch2.commands()))
                .thenReturn(new DispatchResult(1, 1));

        // when
        orchestrationService.orchestrate(
                Instant.parse("2025-01-01T00:00:00Z")
        );

        // then
        verify(dispatchPolicyService, times(1))
                .findCurrentActivePolicy();

        verify(batchService, times(3))
                .loadAndPrepareBatch(
                        any(),
                        anyString(),
                        anyInt(),
                        anyLong(),
                        anyInt()
                );

        verify(asyncExecutor, times(2))
                .execute(anyList());

        verify(asyncExecutor).shutdown();
    }

    /**
     * 최초 batch 가 empty 인 경우
     * - execute() 는 호출되지 않는다
     * - shutdown() 은 호출된다
     */
    @Test
    void orchestrate_noCandidates() throws InterruptedException {

        // given
        DispatchPolicy policy = mockPolicy();
        when(dispatchPolicyService.findCurrentActivePolicy())
                .thenReturn(policy);

        when(batchService.loadAndPrepareBatch(
                any(), anyString(), anyInt(), anyLong(), anyInt()
        )).thenReturn(BatchResult.empty(0L));

        // when
        orchestrationService.orchestrate(
                Instant.parse("2025-01-01T00:00:00Z")
        );

        // then
        verify(asyncExecutor, never()).execute(anyList());
        verify(asyncExecutor).shutdown();
    }

    /* ===========================
       테스트 헬퍼
       =========================== */

    private DispatchPolicy mockPolicy() {
        DispatchPolicy policy = mock(DispatchPolicy.class);
        when(policy.getRoutingPolicy())
                .thenReturn(
                        new ChannelRoutingPolicy(
                                new ChannelSequence(
                                        List.of(ChannelType.SMS, ChannelType.EMAIL)
                                )
                        )
                );
        return policy;
    }

    private com.tr1l.dispatch.application.command.DispatchCommand mockCommand() {
        return mock(com.tr1l.dispatch.application.command.DispatchCommand.class);
    }
}
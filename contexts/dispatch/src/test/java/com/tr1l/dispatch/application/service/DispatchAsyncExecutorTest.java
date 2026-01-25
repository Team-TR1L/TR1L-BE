package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.DispatchCommand;
import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.vo.DispatchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchAsyncExecutorTest {

    @InjectMocks
    private DispatchAsyncExecutor executor;

    @Mock
    private DispatchEventPublisher eventPublisher;

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
    }

    /**
     * 모든 메시지가 정상적으로 발행되는 경우
     * - success = commands.size()
     * - failed = 0
     * - flush() 호출됨
     */
    @Test
    void execute_allSuccess() {

        // given
        List<DispatchCommand> commands = List.of(
                command(1L),
                command(2L),
                command(3L)
        );

        // publish 는 아무 예외도 발생하지 않음
        doNothing().when(eventPublisher).publish(
                anyLong(),
                any(),
                any(),
                anyString(),
                anyString(),
                anyString()
        );

        // when
        DispatchResult result = executor.execute(commands);

        // then
        assertEquals(3, result.success());
        assertEquals(0, result.failed());

        verify(eventPublisher, times(3))
                .publish(anyLong(), any(), any(), anyString(), anyString(), anyString());

        verify(eventPublisher).flush();
    }

    /**
     * 일부 메시지가 실패하는 경우
     * - 실패한 메시지는 failed 카운트 증가
     * - 성공 메시지는 success 카운트 증가
     * - 전체 처리는 중단되지 않는다
     */
    @Test
    void execute_partialFailure() {

        // given
        List<DispatchCommand> commands = List.of(
                command(1L),
                command(2L),
                command(3L)
        );

        // userId=2 인 경우 예외 발생
        doAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            if (userId == 2L) {
                throw new RuntimeException("Kafka error");
            }
            return null;
        }).when(eventPublisher).publish(
                anyLong(),
                any(),
                any(),
                anyString(),
                anyString(),
                anyString()
        );

        // when
        DispatchResult result = executor.execute(commands);

        // then
        assertEquals(2, result.success());
        assertEquals(1, result.failed());

        verify(eventPublisher, times(3))
                .publish(anyLong(), any(), any(), anyString(), anyString(), anyString());

        verify(eventPublisher).flush();
    }

    /**
     * command 가 비어 있는 경우
     * - publish 호출 없음
     * - 결과는 success=0, failed=0
     * - flush 는 호출됨
     */
    @Test
    void execute_emptyCommands() {

        // when
        DispatchResult result = executor.execute(List.of());

        // then
        assertEquals(0, result.success());
        assertEquals(0, result.failed());

        verify(eventPublisher, never()).publish(
                anyLong(), any(), any(), any(), anyString(), anyString()
        );
        verify(eventPublisher).flush();
    }

    /* ===========================
       테스트 헬퍼
       =========================== */

    private DispatchCommand command(Long userId) {
        return new DispatchCommand(
                userId,
                LocalDate.of(2025, 1, 1),
                ChannelType.SMS,
                "s3://mock",
                "asdassdd",
                "01012341234"
        );
    }
}
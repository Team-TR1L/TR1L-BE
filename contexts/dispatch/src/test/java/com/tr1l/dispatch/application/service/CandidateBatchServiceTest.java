package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.DispatchCommand;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.vo.AdminId;
import com.tr1l.dispatch.domain.model.vo.BatchResult;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.ChannelSequence;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetId;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import com.tr1l.dispatch.infra.s3.S3LocationMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateBatchServiceTest {

    @InjectMocks
    private CandidateBatchService candidateBatchService;

    @Mock
    private MessageCandidateJpaRepository candidateRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private S3LocationMapper s3LocationMapper;

    /**
     * 후보가 없는 경우
     * - BatchResult.empty() 가 반환되어야 한다
     * - Repository 조회는 수행되지만
     * - S3LocationMapper, EntityManager.clear() 는 호출되지 않는다
     */
    @Test
    void loadAndPrepareBatch_emptyCandidates() {

        // given
        DispatchPolicy policy = mockDispatchPolicy(List.of(ChannelType.SMS, ChannelType.EMAIL));

        when(candidateRepository.findReadyCandidatesByUserCursorNative(
                any(), anyString(), anyInt(), anyInt(), anyInt()
        )).thenReturn(Collections.emptyList());

        // when
        BatchResult result = candidateBatchService.loadAndPrepareBatch(
                policy,
                "01",
                10,
                0L,
                1000
        );

        // then
        assertTrue(result.isEmpty());
        assertEquals(0L, result.lastUserId());
        assertTrue(result.commands().isEmpty());

        verify(candidateRepository).findReadyCandidatesByUserCursorNative(
                any(), anyString(), anyInt(), anyInt(), anyInt()
        );
        verifyNoInteractions(s3LocationMapper);
        verify(entityManager, never()).clear();
    }

    /**
     * 후보가 존재하는 경우
     * - DispatchCommand 가 후보 수만큼 생성된다
     * - attemptCount 에 따라 channel 이 올바르게 선택된다
     * - lastUserId 는 마지막 후보의 userId 로 이동한다
     * - EntityManager.clear() 가 호출된다
     */
    @Test
    void loadAndPrepareBatch_success() {

        // given
        DispatchPolicy policy = mockDispatchPolicy(List.of(ChannelType.SMS, ChannelType.EMAIL));

        BillingTargetEntity candidate1 = mockCandidate(
                1L, 0, LocalDate.of(2025, 1, 1)
        );
        BillingTargetEntity candidate2 = mockCandidate(
                2L, 1, LocalDate.of(2025, 1, 1)
        );

        when(candidateRepository.findReadyCandidatesByUserCursorNative(
                any(), anyString(), anyInt(), anyInt(), anyInt()
        )).thenReturn(List.of(candidate1, candidate2));

        when(s3LocationMapper.extractValueByChannel(any(), any()))
                .thenReturn("01012341234");

        // when
        BatchResult result = candidateBatchService.loadAndPrepareBatch(
                policy,
                "01",
                10,
                0L,
                1000
        );

        // then
        assertFalse(result.isEmpty());
        assertEquals(2, result.commands().size());
        assertEquals(2L, result.lastUserId());

        DispatchCommand first = result.commands().get(0);
        DispatchCommand second = result.commands().get(1);

        // 첫 번째는 attemptCount=0 → SMS
        assertEquals(ChannelType.SMS, first.channelType());
        // 두 번째는 attemptCount=1 → EMAIL
        assertEquals(ChannelType.EMAIL, second.channelType());

        verify(s3LocationMapper, times(2))
                .extractValueByChannel(any(), any());
        verify(entityManager).clear();
    }

    private DispatchPolicy mockDispatchPolicy(List<ChannelType> channels) {

        DispatchPolicy policy = DispatchPolicy.create(AdminId.of(1L),
                new ChannelRoutingPolicy(new ChannelSequence(List.of(ChannelType.SMS, ChannelType.EMAIL))));

        policy.changeRoutingPolicy(
                new ChannelRoutingPolicy(
                        new ChannelSequence(channels)
                ),
                AdminId.of(1L)
        );

        return policy;
    }

    /* ===========================
       테스트 헬퍼 메서드
       =========================== */

    /**
     * BillingTargetEntity 및 복합키 mocking
     */
    private BillingTargetEntity mockCandidate(
            Long userId,
            int attemptCount,
            LocalDate billingMonth
    ) {
        BillingTargetEntity entity = mock(BillingTargetEntity.class);
        BillingTargetId id = mock(BillingTargetId.class);

        when(id.getUserId()).thenReturn(userId);
        when(id.getBillingMonth()).thenReturn(billingMonth);

        when(entity.getId()).thenReturn(id);
        when(entity.getAttemptCount()).thenReturn(attemptCount);
        when(entity.getS3UrlJsonb()).thenReturn("s3-json");
        when(entity.getSendOptionJsonb()).thenReturn("send-json");

        return entity;
    }
}
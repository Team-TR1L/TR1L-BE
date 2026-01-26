package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.domain.dto.*;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.ChannelSequence;
import com.tr1l.dispatch.infra.persistence.repository.DispatchPolicyRepository;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchPolicyServiceDashboardTest {

    @Mock
    private DispatchPolicyRepository repository;

    @Mock
    private MessageCandidateJpaRepository messageRepository;

    @Spy
    @InjectMocks
    private DispatchPolicyService service;

    private MockedStatic<LocalDate> localDateMock;

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 26);
    private static final LocalDate START_DATE = TODAY.minusDays(6);
    private static final LocalDate BILLING_MONTH = LocalDate.of(2025, 12, 1);

    @BeforeEach
    void setUp() {

        localDateMock = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
        localDateMock.when(LocalDate::now).thenReturn(TODAY);

        DispatchPolicy policy = mock(DispatchPolicy.class);
        ChannelRoutingPolicy routingPolicy = mock(ChannelRoutingPolicy.class);
        ChannelSequence primaryOrder =
                mock(ChannelSequence.class);

        when(primaryOrder.channels())
                .thenReturn(List.of(ChannelType.SMS, ChannelType.EMAIL));

        when(routingPolicy.getPrimaryOrder())
                .thenReturn(primaryOrder);

        when(policy.getRoutingPolicy())
                .thenReturn(routingPolicy);

        doReturn(policy)
                .when(service)
                .findCurrentActivePolicy();
    }


    @AfterEach
    void tearDown() {
        localDateMock.close();
    }

    @Test
    void 대시보드_통계_정상_조회() {

        Set<LocalDate> billingMonths = Set.of(BILLING_MONTH);

        when(messageRepository.countByBillingMonthAndDayTime(
                billingMonths,
                "20",
                "26"
        )).thenReturn(List.of(
                new BillingDailyCount(BILLING_MONTH, "20", 10L),
                new BillingDailyCount(BILLING_MONTH, "26", 30L)
        ));

        when(messageRepository.countByAttemptCountAndBillingMonth(
                List.of(0, 1),
                billingMonths
        )).thenReturn(List.of(
                new AttemptChannelCount(0, 40L),
                new AttemptChannelCount(1, 60L)
        ));

        when(messageRepository.countTodayResult(
                BILLING_MONTH,
                "26"
        )).thenReturn(new BillingResultCount(80L, 20L));

        DashboardStatsDto result = service.getDashboardStats();

        assertThat(result.getTodaySent()).isEqualTo(30L);
        assertThat(result.getSuccessRate()).isEqualTo(80.0);
        assertThat(result.getFailureRate()).isEqualTo(20.0);

        assertThat(result.getDailyTrend()).hasSize(7);

        DailyTrendDto todayTrend = result.getDailyTrend().stream()
                .filter(t -> t.getDate().equals("1/26"))
                .findFirst()
                .orElseThrow();

        assertThat(todayTrend.getSent()).isEqualTo(30L);

        assertThat(result.getChannelDistribution())
                .extracting(ChannelDistributionDto::getName,
                        ChannelDistributionDto::getValue)
                .containsExactlyInAnyOrder(
                        tuple("SMS", 40),
                        tuple("EMAIL", 60)
                );
    }
}
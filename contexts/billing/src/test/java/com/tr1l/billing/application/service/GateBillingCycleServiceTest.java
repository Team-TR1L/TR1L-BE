package com.tr1l.billing.application.service;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;
import com.tr1l.billing.application.port.out.BillingCycleGatePort;
import com.tr1l.billing.application.port.out.BillingTargetMemberPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 정산 월 첫 실행 때 청구 대상 명단을 고정하는지 (TR1L-179)
class GateBillingCycleServiceTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 1);
    private static final Instant CUTOFF = Instant.parse("2026-02-01T00:00:00Z");

    private final List<YearMonth> frozen = new ArrayList<>();
    private final BillingTargetMemberPort memberPort = new BillingTargetMemberPort() {
        @Override
        public int freezeIfAbsent(YearMonth billingMonth) {
            frozen.add(billingMonth);
            return 1;
        }

        @Override
        public Set<Long> membersAmong(YearMonth billingMonth, Collection<Long> userIds) {
            return Set.of();
        }
    };

    @Test
    @DisplayName("마감되지 않은 월이면 명단을 고정하고 진행하는지 보기")
    void runningMonthShouldFreezeMembers() {
        GateBillingCycleService service = new GateBillingCycleService(gate("RUNNING"), memberPort);

        GateBillingCycleUseCase.GateResult result = service.gate(new GateBillingCycleUseCase.GateCommand(MONTH, CUTOFF));

        assertEquals(GateBillingCycleUseCase.Decision.PROCEED, result.decision());
        assertEquals(List.of(MONTH), frozen);
    }

    @Test
    @DisplayName("마감된 월이면 명단을 건드리지 않고 NOOP 인지 보기")
    void finishedMonthShouldNotTouchMembers() {
        GateBillingCycleService service = new GateBillingCycleService(gate("FINISHED"), memberPort);

        GateBillingCycleUseCase.GateResult result = service.gate(new GateBillingCycleUseCase.GateCommand(MONTH, CUTOFF));

        assertEquals(GateBillingCycleUseCase.Decision.NOOP, result.decision());
        assertEquals(List.of(), frozen);
    }

    private BillingCycleGatePort gate(String status) {
        return (billingMonth, cutoffAt) -> new BillingCycleGatePort.GateRow(billingMonth, cutoffAt, status);
    }
}

package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.port.DispatchPolicyRepository;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchPolicyServiceTest {

    @Mock
    private DispatchPolicyRepository repository;

    @InjectMocks
    private DispatchPolicyService service;

    @Test
    void 정책을_생성한다() {
        // given
        ChannelRoutingPolicy routingPolicy = mock(ChannelRoutingPolicy.class);
        CreateDispatchPolicyCommand command =
                new CreateDispatchPolicyCommand(1L, routingPolicy);

        ArgumentCaptor<DispatchPolicy> captor =
                ArgumentCaptor.forClass(DispatchPolicy.class);

        // when
        Long policyId = service.createPolicy(command);

        // then
        verify(repository).save(captor.capture());
        DispatchPolicy savedPolicy = captor.getValue();

        assertThat(savedPolicy.getDispatchPolicyId().value())
                .isEqualTo(policyId);
        assertThat(savedPolicy.getStatus())
                .isEqualTo(PolicyStatus.DRAFT);
        assertThat(savedPolicy.getRoutingPolicy())
                .isEqualTo(routingPolicy);
    }

    @Test
    void 정책_목록을_조회한다() {
        // given
        DispatchPolicy policy = mock(DispatchPolicy.class);
        when(repository.findAll())
                .thenReturn(List.of(policy));

        // when
        List<DispatchPolicy> result = service.findPolicies();

        // then
        assertThat(result).hasSize(1);
        verify(repository).findAll();
    }

    @Test
    void 정책_단건을_조회한다() {
        // given
        DispatchPolicy policy = mock(DispatchPolicy.class);
        when(repository.findById(1L))
                .thenReturn(Optional.of(policy));

        // when
        DispatchPolicy result = service.findPolicy(1L);

        // then
        assertThat(result).isSameAs(policy);
    }

    @Test
    void 정책이_없으면_예외를_던진다() {
        // given
        when(repository.findById(1L))
                .thenReturn(Optional.empty());

        // expect
        assertThatThrownBy(() -> service.findPolicy(1L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 정책을_삭제한다() {
        // given
        ChannelRoutingPolicy routingPolicy = mock(ChannelRoutingPolicy.class);

        DispatchPolicy policy = DispatchPolicy.create(
                DispatchPolicyId.generatePolicyId().value(),
                1L,
                routingPolicy
        );

        when(repository.findById(policy.getDispatchPolicyId().value()))
                .thenReturn(Optional.of(policy));

        // when
        service.deletePolicy(policy.getDispatchPolicyId().value());

        // then
        assertThat(policy.getStatus())
                .isEqualTo(PolicyStatus.RETIRED);

        verify(repository).save(policy);
    }
}

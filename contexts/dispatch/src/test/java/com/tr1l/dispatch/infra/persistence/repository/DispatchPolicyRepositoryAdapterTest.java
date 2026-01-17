package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.enums.PolicyStatus;
import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import com.tr1l.dispatch.domain.model.vo.ChannelSequence;
import com.tr1l.dispatch.domain.model.vo.DispatchPolicyId;
import com.tr1l.dispatch.domain.model.vo.PolicyVersion;
import com.tr1l.dispatch.infra.persistence.mapper.DispatchPolicyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

//DataJpaTest 관련 오류 발생함
//의존성 문제?

@DataJpaTest
@Import({
        DispatchPolicyRepositoryAdapter.class,
        DispatchPolicyMapper.class
})
class DispatchPolicyRepositoryAdapterTest {

    @Autowired
    private DispatchPolicyRepositoryAdapter adapter;

    @Test
    void 정책을_DB에_저장하고_ID로_조회한다() {
        // given
        ChannelRoutingPolicy routingPolicy
                = new ChannelRoutingPolicy(new ChannelSequence(List.of(new ChannelType[]{ChannelType.EMAIL, ChannelType.SMS})));

        DispatchPolicy policy = DispatchPolicy.create(
                DispatchPolicyId.generatePolicyId().value(),
                1L,
                routingPolicy
        );

        // when
        adapter.save(policy);

        Optional<DispatchPolicy> result =
                adapter.findById(policy.getDispatchPolicyId().value());

        // then
        assertThat(result).isPresent();

        DispatchPolicy loaded = result.get();
        assertThat(loaded.getDispatchPolicyId())
                .isEqualTo(policy.getDispatchPolicyId());
        assertThat(loaded.getStatus())
                .isEqualTo(PolicyStatus.DRAFT);
        assertThat(loaded.getVersion())
                .isEqualTo(PolicyVersion.of(1));
        assertThat(loaded.getRoutingPolicy())
                .isNotNull();
    }

    @Test
    void 정책_전체를_조회한다() {
        // given
        DispatchPolicy policy1 = DispatchPolicy.create(
                DispatchPolicyId.generatePolicyId().value(),
                1L,
                new ChannelRoutingPolicy(new ChannelSequence(List.of()))
        );

        DispatchPolicy policy2 = DispatchPolicy.create(
                DispatchPolicyId.generatePolicyId().value(),
                2L,
                new ChannelRoutingPolicy(new ChannelSequence(List.of(new ChannelType[]{ChannelType.EMAIL, ChannelType.SMS})))
        );

        adapter.save(policy1);
        adapter.save(policy2);

        // when
        List<DispatchPolicy> policies = adapter.findAll();

        // then
        assertThat(policies).hasSize(2);
    }
}
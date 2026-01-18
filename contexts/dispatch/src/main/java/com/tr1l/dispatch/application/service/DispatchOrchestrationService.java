package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.port.in.DispatchOrchestrationUseCase;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.infra.persistence.entity.MessageCandidateEntity;
import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DispatchOrchestrationService implements DispatchOrchestrationUseCase {

    private final MessageCandidateJpaRepository candidateRepository;
    private final DispatchPolicyService dispatchPolicyService;
    private final DispatchEventPublisher eventPublisher;

    @Transactional
    public void orchestrate(Instant now) {
        //1. 현재 발송 가능한 메시지들을 가져온다.
        List<MessageCandidateEntity> candidates = candidateRepository.findReadyCandidates(now);

        //2. 발송 정책과 메시지의 발송 채널 타입을 대조한다.
        DispatchPolicy policy = dispatchPolicyService.findCurrentActivePolicy();

        List<ChannelType> channels =
                policy.getRoutingPolicy().getPrimaryOrder().channels();

        for (MessageCandidateEntity candidate : candidates) {
            if (candidate.getAttemptCount() >= channels.size()) {
                //Todo: 추후 log나 다른 방식으로 수정 가능
                throw new DispatchDomainException(DispatchErrorCode.NO_MORE_RETRY);
            }

            if (!channels.get(candidate.getAttemptCount())
                    .equals(candidate.getChannel())) {
                //Todo: 추후 log나 다른 방식으로 수정 가능
                throw new DispatchDomainException(DispatchErrorCode.POLICY_NOT_EQUAL);
            }
        }

        for(MessageCandidateEntity candidate : candidates) {
        //3. 메시지의 정책을 PROCESSING으로 수정하고, Kafka에 이벤트 발행
            candidate.markProcessing();

            eventPublisher.publish(
                    candidate.getUserId(),
                    policy.getDispatchPolicyId().value(),
                    candidate.getChannel(),
                    candidate.getAttemptCount(),
                    candidate.getEncryptedS3Url(),
                    candidate.getEncryptedDestination()
            );
        }
    }
}

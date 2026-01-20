package com.tr1l.dispatch.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.application.port.in.DispatchOrchestrationUseCase;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DispatchOrchestrationService implements DispatchOrchestrationUseCase {

    private final MessageCandidateJpaRepository candidateRepository;
    private final DispatchPolicyService dispatchPolicyService;
    private final DispatchEventPublisher eventPublisher;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void orchestrate(Instant now) {
        //1. 발송 정책을 조회한다.

        DispatchPolicy policy = dispatchPolicyService.findCurrentActivePolicy();
        //2. 현재 발송 가능한 메시지들을 가져온다.

        int currentHour = LocalDateTime.now(ZoneId.of("Asia/Seoul")).getHour();

        List<BillingTargetEntity> candidates =
                candidateRepository.findReadyCandidates(currentHour, PageRequest.of(0, 500));

        List<ChannelType> channels =
                policy.getRoutingPolicy().getPrimaryOrder().channels();

        //3.  json 확인하고 Kafka에 이벤트 발행
        for(BillingTargetEntity candidate : candidates) {
            ChannelType nowChannel = policy.getRoutingPolicy().getPrimaryOrder()
                    .channels().get(candidate.getAttemptCount());

            String s3url = extractValueByChannel(
                    candidate.getS3UrlJsonb(),
                    nowChannel
            );

            String destination = extractValueByChannel(
                    candidate.getSendOptionJsonb(),
                    nowChannel
            );

            eventPublisher.publish(
                    candidate.getId().getUserId(),  //유저 아이디
                    candidate.getId().getBillingMonth(), // billing month
                    nowChannel,
                    s3url,
                    destination
            );
        }
    }
    private String extractValueByChannel(String json, ChannelType channelType) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            for (JsonNode node : root) {
                String key = node.path("key").asText();
                if (key.equalsIgnoreCase(channelType.name())) {
                    return node.path("value").asText();
                }
            }
            return null;

        } catch (Exception e) {
            throw new DispatchDomainException(DispatchErrorCode.JSON_MAPPING_ERROR, e);
        }
    }

}

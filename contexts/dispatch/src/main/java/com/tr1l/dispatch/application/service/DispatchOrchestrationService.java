package com.tr1l.dispatch.application.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.thirdparty.jackson.core.JsonProcessingException;

import java.time.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOrchestrationService implements DispatchOrchestrationUseCase {

    private final MessageCandidateJpaRepository candidateRepository;
    private final DispatchPolicyService dispatchPolicyService;
    private final DispatchEventPublisher eventPublisher;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void orchestrate(Instant now) {
        Instant startTime = Instant.now();
        log.warn("🕒 Step 0: 오케스트레이션 시작 - {}", startTime);

        //1. 발송 정책을 조회한다.
        log.warn("🔍 Step 1: 활성 발송 정책 조회 중...");
        DispatchPolicy policy = dispatchPolicyService.findCurrentActivePolicy();

        List<ChannelType> channels =
                policy.getRoutingPolicy().getPrimaryOrder().channels();

        //2. 현재 발송 가능한 메시지들을 가져온다.
        int currentHour = LocalDateTime.now(ZoneId.of("Asia/Seoul")).getHour();

        log.warn("📦 Step 2: 후보 조회 시작...");
        List<BillingTargetEntity> candidates =
                candidateRepository.findReadyCandidates(channels.size() - 1,
                        String.format("%02d", LocalDate.now().getDayOfMonth()),
                        currentHour);

        log.warn("후보군 사이즈: {}", candidates.size());

        //3.  json 확인하고 Kafka에 이벤트 발행
        for(BillingTargetEntity candidate : candidates) {
            ChannelType nowChannel = channels.get(Math.min(channels.size() - 1, candidate.getAttemptCount()));

            String s3url = extractLocationValueByChannel(
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
        Instant endTime = Instant.now();
        log.warn("✅ 오케스트레이션 시작: {}, 종료: {}, 소요 시간(ms): {}"
                , startTime, endTime, Duration.between(startTime, endTime).toMillis());
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

    public String extractLocationValueByChannel(String jsonb, ChannelType nowChannel) {
        // 1. 데이터가 null이거나 빈 배열인 경우 조기 리턴 (또는 null 반환)
        if (jsonb == null || jsonb.trim().equals("[]") || jsonb.isBlank()) {
            log.warn("S3 URL JSON 데이터가 비어 있습니다. Skip 처리합니다.");
            return null; // 호출부에서 null 체크 후 발송 대상에서 제외하도록 설계
        }

        try {
            List<S3Location> locations = objectMapper.readValue(jsonb, new TypeReference<List<S3Location>>() {});

            return locations.stream()
                    .filter(loc -> loc.key().equalsIgnoreCase(nowChannel.name()))
                    .findFirst()
                    .map(loc -> {
                        String bucketName = loc.bucket();
                        return String.format("https://%s.s3.ap-northeast-2.amazonaws.com/%s",
                                bucketName, loc.s3Key());
                    })
                    // 2. 해당 채널(EMAIL/SMS)만 없는 경우
                    .orElseGet(() -> {
                        log.warn("해당 채널[{}]에 대한 S3 설정을 찾을 수 없습니다. 데이터: {}", nowChannel, jsonb);
                        return null;
                    });

        } catch (Exception e) {
            log.error("S3 URL 생성 중 예상치 못한 오류: {}", e.getMessage());
            throw new DispatchDomainException(DispatchErrorCode.S3_URL_FAILED);
        }
    }

    public static record S3Location(
            String key,
            String bucket,
            @JsonProperty("s3_key") String s3Key
    ) {}
}

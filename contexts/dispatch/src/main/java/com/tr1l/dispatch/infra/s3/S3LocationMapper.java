package com.tr1l.dispatch.infra.s3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class S3LocationMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractValueByChannel(String json, ChannelType channelType) {
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

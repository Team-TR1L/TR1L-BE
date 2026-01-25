package com.tr1l.dispatch.infra.s3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3LocationMapper {
    private final ObjectMapper objectMapper;

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

    public S3LocationDTO extractLocation(String jsonb, ChannelType channel) {
        // 1. 입력값이 없으면 즉시 기본 객체 반환
        if (jsonb == null || jsonb.isEmpty()) {
            return new S3LocationDTO("", "", "");
        }

        try {
            List<S3LocationDTO> locations = objectMapper.readValue(
                    jsonb,
                    new TypeReference<List<S3LocationDTO>>() {}
            );

            // 2. 찾지 못하더라도 예외를 던지지 않고 기본 객체 반환
            return locations.stream()
                    .filter(loc -> loc.key() != null && channel.name().equalsIgnoreCase(loc.key()))
                    .findFirst()
                    .orElseGet(() -> new S3LocationDTO("", "", ""));

        } catch (Exception e) {
            // 3. 파싱 에러가 나더라도 로그만 남기고 흐름을 유지
            log.error("S3 URL JSON 파싱 실패 (데이터: {}), 에러: {}", jsonb, e.getMessage());
            return new S3LocationDTO("", "", "");
        }
    }

    public record S3LocationDTO(
            String key,
            String bucket,
            @JsonProperty("s3_key")
            String s3Key
    ) {}
}

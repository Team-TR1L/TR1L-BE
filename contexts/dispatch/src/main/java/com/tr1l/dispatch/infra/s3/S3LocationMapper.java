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
        if (jsonb == null || jsonb.isEmpty()) {
            throw new IllegalArgumentException("S3 URL JSON 데이터가 비어 있습니다.");
        }

        List<S3LocationDTO> locations;
        try {
            // 1. 파싱만 try-catch로 감쌉니다.
            locations = objectMapper.readValue(
                    jsonb,
                    new TypeReference<List<S3LocationDTO>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("S3 URL JSON 파싱 중 오류 발생", e);
        }

        // 2. 파싱된 결과에서 찾는 과정은 try-catch 밖에서 수행하여 예외 메시지를 보존합니다.
        return locations.stream()
                .filter(loc -> channel.name().equalsIgnoreCase(loc.key()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        String.format("해당 채널(%s)의 S3 정보를 찾을 수 없습니다. 데이터: %s", channel, jsonb)
                ));
    }

    public record S3LocationDTO(
            String key,
            String bucket,
            @JsonProperty("s3_key")
            String s3Key
    ) {}
}

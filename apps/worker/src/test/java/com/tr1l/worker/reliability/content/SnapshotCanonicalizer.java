package com.tr1l.worker.reliability.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// snapshot 정규화와 해시
// 실행마다 달라지는 시각 필드 제외
// 키 순서 고정, 배열 순서 유지
public final class SnapshotCanonicalizer {
    // 실행 시각에 따라 바뀌는 최상위 필드
    public static final Set<String> VOLATILE_FIELDS = Set.of("createdAt", "issuedAt", "updatedAt");

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public String canonicalJson(Document snapshot) {
        Map<String, Object> normalized = new TreeMap<>();
        snapshot.forEach((key, value) -> {
            if (!VOLATILE_FIELDS.contains(key)) {
                normalized.put(key, normalize(value));
            }
        });

        try {
            return mapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonicalize snapshot: " + snapshot.get("workId"), e);
        }
    }

    public String sha256(Document snapshot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(snapshot).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // 중첩 문서 키 정렬
    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), normalize(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            list.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        return value;
    }
}

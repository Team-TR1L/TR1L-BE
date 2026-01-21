package com.tr1l.util;


import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/*==========================
 * Spring Resource(SQL 파일)를 문자열로 읽어 캐싱
 *
 * - @Value로 주입된 Resource 그대로 사용
 * - 동일 Resource 인스턴스에 대해 1회만 I/O 수행 후 캐싱
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 18.]
 * @version 1.0
 *==========================*/
@Component
public class SqlResourceReader {
    private final Map<Resource, String> cache = new ConcurrentHashMap<>();

    public String read(Resource resource) {
        if (resource == null) {
            //TODO:전역 에러로 처리
            throw new IllegalArgumentException("resource is null");
        }

        return cache.computeIfAbsent(resource, r -> {
            try (var in = r.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read SQL resource: " + safeDesc(r), e);
            }
        });
    }

    /** 캐시를 강제로 비우고 싶을 때 사용 */
    public void clear() {
        cache.clear();
    }

    private String safeDesc(Resource r) {
        try {
            return r.getDescription();
        } catch (Exception ignored) {
            return String.valueOf(r);
        }
    }
}

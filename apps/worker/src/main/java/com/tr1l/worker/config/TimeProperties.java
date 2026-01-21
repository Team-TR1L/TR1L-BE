package com.tr1l.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "time")
public record TimeProperties(
        String format,
        String zone
) {
    public TimeProperties {
        if (format == null || format.isBlank()) format = "yyyy-MM-dd HH:mm:ss";
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
    }
}

package com.tr1l.worker.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/*==========================
 * Batch Job 설정 클래스
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 20.]
 * @version 1.0
 *==========================*/
@Configuration
@RequiredArgsConstructor
@Getter
@Slf4j
public class BatchJobConfiguration {
    @Value("${spring.batch.job.name:}")
    private String jobName;

    @Value("${batch.job1StartTime:}")
    private String job1StartTime;

    @Value("${batch.job2StartTime:}")
    private String job2StartTime;

    @Value("${batch.channelOrder:}")
    private String channelOrder;


    private final ObjectMapper mapper;

    @PostConstruct
    public void logConfiguration() {
        log.info("=== Batch Configuration ===");
        log.info("jobName: '{}'", jobName);
        log.info("job1StartTime: '{}'", job1StartTime);
        log.info("job2StartTime: '{}'", job2StartTime);
        log.info("channelOrder: '{}'", channelOrder);
        log.info("===========================");
    }

    /**
     * channelOrder : Json -> String 파싱
     */
    public String getChannelOrder()  {
        if (channelOrder == null || channelOrder.isEmpty()) {
            throw new IllegalArgumentException("필수 파라미터 값인 channelOrder를 입력하지 않았습니다.");
        }

        return channelOrder;
    }

    /**
     * Job1 시작 시간 -> Instant 파싱
     */
    public Instant getJob1StartTimeAsInstant() {
        if (job1StartTime == null || job1StartTime.isBlank()) {
            throw new IllegalArgumentException("batch.job1StatTime 값이 입력되지 않았습니다.");
        }

        return Instant.parse(job1StartTime);
    }

    /**
     * Job2 시작 시간 -> Instant 파싱
     */
    public Instant getJob2StartTimeAsInstant() {
        if (job2StartTime == null || job2StartTime.isBlank()) {
            throw new IllegalArgumentException("batch.job2StatTime 값이 입력되지 않았습니다.");
        }

        return Instant.parse(job2StartTime);
    }
}

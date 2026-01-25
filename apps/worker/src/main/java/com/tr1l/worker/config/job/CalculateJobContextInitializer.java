package com.tr1l.worker.config.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class CalculateJobContextInitializer implements JobExecutionListener {
    @Value("${time.zone}")
    private String timeZone;

    private final JdbcTemplate mainJdbcTemplate;

    public CalculateJobContextInitializer(
            @Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbcTemplate
    ) {
        this.mainJdbcTemplate = mainJdbcTemplate;
    }

    public static final String CTX_CUTOFF_AT = "cutoff";
    public static final String CTX_BILLING_YM = "billingYearMonth";
    public static final String CTX_START_DATE = "startDate";
    public static final String CTX_END_DATE = "endDate";
    public static final String CTX_CHANNEL_ORDER = "channelOrder";
    public static final String CTX_MAX_USER_ID = "maxUserId";


    @Override
    public void beforeJob(JobExecution jobExecution) {
        JobParameters params = jobExecution.getJobParameters();

        // 1) cutoff 파라미터 파싱
        String cutoffAtRaw = params.getString("cutoff");
        if (cutoffAtRaw == null || cutoffAtRaw.isBlank()) {
            throw new IllegalArgumentException("JobParameter 'cutoff' is required.");
        }

        // 2) cutoffAt을 Instant로 파싱하고 한국 시간대 변환
        Instant cutoffAt = Instant.parse(cutoffAtRaw);
        ZonedDateTime seoulTime = ZonedDateTime.ofInstant(cutoffAt, ZoneId.of(timeZone));

        // 3) 정산 대상 월 계산
        YearMonth billingYm = YearMonth.from(seoulTime.minusMonths(1));

        // 4) 시작일 및 종료일 계산
        LocalDate startDate = billingYm.atDay(1);  // 해당 월의 1일
        LocalDate endDate = billingYm.atEndOfMonth();         // 해당 월의 마지막 날

        // 5) channelOrder 파싱
        String channelOrder = params.getString("channelOrder");

        // 6) 포맷터 정의
        DateTimeFormatter ymFormat = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 7) ExecutionContext에 저장
        ExecutionContext ctx = jobExecution.getExecutionContext();
        ctx.putString(CTX_CUTOFF_AT, cutoffAt.toString());
        ctx.putString(CTX_BILLING_YM, billingYm.format(ymFormat));
        ctx.putString(CTX_START_DATE, startDate.format(dateFormat));
        ctx.putString(CTX_END_DATE, endDate.format(dateFormat));
        ctx.put(CTX_CHANNEL_ORDER, channelOrder);
        ctx.putLong(CTX_MAX_USER_ID, fetchMaxUserId());

        // 8) 로그 출력
        log.info("=== Calculate Job Context Initialized ===");
        log.info("Cutoff At       : {}", cutoffAt);
        log.info("Seoul Time      : {}", seoulTime);
        log.info("Billing YM      : {}", ctx.getString(CTX_BILLING_YM));
        log.info("Start Date      : {}", ctx.getString(CTX_START_DATE));
        log.info("End Date        : {}", ctx.getString(CTX_END_DATE));
        log.info("Channel Order   : {}", channelOrder);
        log.info("Max User Id     : {}", ctx.getLong(CTX_MAX_USER_ID));
        log.info("==========================================");
    }

    private long fetchMaxUserId() {
        Long max = mainJdbcTemplate.queryForObject("SELECT MAX(user_id) FROM users", Long.class);
        return max == null ? 0L : max;
    }
}

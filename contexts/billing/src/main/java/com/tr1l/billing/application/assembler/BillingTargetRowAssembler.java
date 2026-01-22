package com.tr1l.billing.application.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.domain.model.enums.WelfareType;
import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;
import com.tr1l.billing.domain.service.BillingCalculator;
import com.tr1l.billing.error.BillingErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * VO를 주입받고, 계산에 필요한 정보들을 BillingCalculationInput으로 생성
 * calculator를 호출해서 Billing 생성
 */
@Slf4j
@Component
public final class BillingTargetRowAssembler {

    private final ObjectMapper objectMapper;
    private final BillingCalculator calculator;

    public BillingTargetRowAssembler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.calculator = new BillingCalculator();
    }


    // 계산기 호출
    public Billing toDraftBilling(
            BillingId billingId,
            CustomerId customerId,
            IdempotencyKey idempotencyKey,
            BillingTargetRow row,
            BillingCalculator.LineIdProvider lineIdProvider
    ) {
        Objects.requireNonNull(billingId);
        log.warn("billingId = {}",billingId);

        Objects.requireNonNull(customerId);
        log.warn("customerId = {}",customerId);

        Objects.requireNonNull(idempotencyKey);
        log.warn("idempotencyKey = {}",idempotencyKey);

        Objects.requireNonNull(row);
        log.warn("row = {}" ,row);

        Objects.requireNonNull(lineIdProvider);
        log.warn("lineIdProvider = {}",lineIdProvider);

        // Aggregate 생성에 필요한 VO 주입
        BillingPeriod period = new BillingPeriod(parseBillingMonth(row.billingMonth())); // 2026-01
        CustomerName customerName = new CustomerName(row.userName()); // 박준희
        CustomerBirthDate customerBirthDate = new CustomerBirthDate(parseBirthYearMonth(row.userBirthDate())); // 2001-02-14
        Recipient recipient = toRecipient(row.recipientEmailEnc(), row.recipientPhoneEnc());
        log.warn("period = {} customerName = {} customerBirthDate = {} recipient = {}", period,customerName, customerBirthDate, recipient);

        // Row -> 할인, 청구라인 계산
        BillingCalculationInput in = toCalculationInput(row);
        log.warn("======= BillingCalculationInput Success ==========");
        log.info("======= BillingCalculationInput={}", in);

        // 계산기 호출: 라인 구성 + 할인 적용 + totals까지 완료된 Billing(DRAFT) 반환
        return calculator.calculateDraft(
                billingId,
                customerId,
                period,
                customerName,
                customerBirthDate,
                recipient,
                idempotencyKey,
                in,
                lineIdProvider
        );
    }

    /**
     * - RDB 값/코드/JSONB를 도메인 정책들이 사용할 수 있는 형태로 정규화
     */
    private BillingCalculationInput toCalculationInput(BillingTargetRow row) {
        log.warn("==================== toCalculationInput Start ============================");
        Money planP = new Money(row.planMonthlyPrice());     // 요금제 정가 P
        log.warn("==================== computeAdditionalUsageFee Start ============================");
        Money usageM = computeAdditionalUsageFee(row);       // 추가 사용량 과금 M -> 0 or 1200원
         log.warn("==================== computeAdditionalUsageFee End ============================");
        // 할인율(0~1)
         log.warn("==================== toRate Start ============================");
        Rate contractRate = toRate(row.contractRate()); // 0.25
        log.warn("==================== toRate End ============================");

        // 복지 값은 welfareEligible 일 때만 세팅
        WelfareType welfareTypeOrNull = null; // 복지 유형
        Rate welfareRateOrNull = null; // 복지 할인률
        Money welfareCapOrNull = null; // 복지 상한선

        log.warn("==================== 복지 분기 Start ============================");
        if (row.welfareEligible()) { // 복지 유저일 경우
            log.warn("==================== toWelfareType Start ============================");
            welfareTypeOrNull = toWelfareType(row.welfareCode()); // 복지코드
            log.warn("==================== toWelfareType End ============================");

            log.warn("==================== toRate2 Start ============================");
            welfareRateOrNull = toRate(row.welfareRate()); // 할인률 -> 0.35
            log.warn("==================== toRate2 End ============================");

            // cap: null 또는 0이면 상한 없음(null로 표현)
            log.warn("==================== 분기 시작 ============================");
            if (row.welfareCapAmount() != null && row.welfareCapAmount() > 0) {
                welfareCapOrNull = new Money(row.welfareCapAmount());
                log.warn("==================== {} , {}  ============================",row.welfareCapAmount(),row.welfareCapAmount());
            }
        }


        // JSONB: [{name, monthlyPrice}] -> AddonLine 리스트
        List<BillingCalculationInput.AddonLine> addonLines = parseAddonLines(row.optionsJsonb());

        log.warn("==================== toCalculationInput End ============================");
        return new BillingCalculationInput(
                planP,
                usageM,
                row.hasContract(),
                contractRate,
                row.soldierEligible(),
                row.welfareEligible(),
                welfareTypeOrNull,
                welfareRateOrNull,
                welfareCapOrNull,
                addonLines
        );
    }


    /**
     * 총 사용량에 따른 추가 과금(M) 계산
     *
     * - DBT-01(완전 무제한): 과금 없음 => 0
     * - DBT-03(초과 없음/차단): 과금 없음 => 0
     * - DBT-02(nMB 이후 과금): max(0, used - included) * perMb
     *
     * perMb는 BigDecimal(0.275원) 이므로 최종 원 단위로 내림(FLOOR)
     */
    private Money computeAdditionalUsageFee(BillingTargetRow row) {
        String code = row.dataBillingTypeCode();
        if (code == null || code.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_DATA_BILLING_TYPE);
        }

        return switch (code) {
            case "DBT-01", "DBT-03" -> Money.zero(); // 추가 사용량에 따른 과금 부여 x

            case "DBT-02" -> {
                Long included = row.includedDataMb(); // 기본 제공량
                BigDecimal perMb = row.excessChargePerMb(); // 0.275고정

                if (included == null) { // DB에서 잘못 들어옴, 기본 제공량은 null이 될 수 없음
                     throw new BillingDomainException(BillingErrorCode.INVALID_INCLUDED_DATA);
                }

                long used = row.usedDataMb(); // 총 사용량, 0도 가능
                long excessMb = Math.max(0, used - included); // 기본 제공량보다 높을 경우에만 과금 부여, 다만 0보다 커야됨

                long feeWon = perMb // 추가 사용량 x 0.275 반올림 처리
                        .multiply(BigDecimal.valueOf(excessMb))
                        .setScale(0, RoundingMode.FLOOR)
                        .longValueExact();

                yield new Money(feeWon);
            }

            default -> throw new BillingDomainException(BillingErrorCode.INVALID_DATA_BILLING_TYPE);
        };
    }

    /**
     * DB의 double(0.25 형태) -> Rate(BigDecimal)로 변환
     * - Rate가 0~1 검증을 하므로 여기서는 BigDecimal.valueOf만 하면 됨.
     */
    private Rate toRate(double v) {
        return new Rate(BigDecimal.valueOf(v));
    }


    /**
     * 복지 코드에 따른 할인률 변경
     */
    private WelfareType toWelfareType(String welfareCode) {
        if (welfareCode == null || welfareCode.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_WELFARE);
        }
        return switch (welfareCode) {
            case "WLF-01" -> WelfareType.NORMAL; // 정상
            case "WLF-02" -> WelfareType.DISABLED; // 장애인
            case "WLF-03" -> WelfareType.NATIONAL_MERIT; // 국가유공자
            case "WLF-04" -> WelfareType.BASIC_LIVELIHOOD; // 기초수급대상자
            case "WLF-05" -> WelfareType.BASIC_LIVELIHOOD; // 기초연급대상자

            default -> throw new BillingDomainException(BillingErrorCode.INVALID_WELFARE);
        };
    }


    /**
     * optionsJsonb 원문(JSONB) 파싱 -> AddonLine 리스트 생성
     * [
     *   { "name":"디즈니+", "monthlyPrice":9405 },
     *   { "name":"티빙",   "monthlyPrice":4950 }
     * ]
     */
    private List<BillingCalculationInput.AddonLine> parseAddonLines(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) {
            return List.of();
        }
        try {
            List<AddonJson> list = objectMapper.readValue(jsonb, new TypeReference<List<AddonJson>>() {});

            return list.stream()
                    .map(a -> new BillingCalculationInput.AddonLine(
                            a.name(),
                            new Money(a.monthlyPrice())
                    ))
                    .toList();

        } catch (Exception e) {
            throw new BillingDomainException(BillingErrorCode.INVALID_ADDON);
        }
    }

    /** JSONB 한 항목 포맷 */
    private record AddonJson(String name, long monthlyPrice) {}


    /** String -> 년월로 변경
     * "2026-01" -> YearMonth
     */
    private YearMonth parseBillingMonth(String billingMonth) {
        if (billingMonth == null || billingMonth.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_PERIOD);
        }
        return YearMonth.parse(billingMonth);
    }

    /**
     * "1999-03-01" -> 1999-03-01
     */
    private LocalDate parseBirthYearMonth(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_USER_BIRTH_DATE);
        }
         return LocalDate.parse(birthDate);
    }

    /**
     * 암호화된 이메일/폰 문자열을 Recipient VO로 변환 -> 추가 테스트 필요
     */
    private Recipient toRecipient(String emailEnc, String phoneEnc) {
        EncryptedEmail email = (emailEnc == null || emailEnc.isBlank()) ? null : new EncryptedEmail(emailEnc);
        EncryptedPhone phone = (phoneEnc == null || phoneEnc.isBlank()) ? null : new EncryptedPhone(phoneEnc);

        return new Recipient(
                java.util.Optional.ofNullable(email),
                java.util.Optional.ofNullable(phone)
        );
    }
}

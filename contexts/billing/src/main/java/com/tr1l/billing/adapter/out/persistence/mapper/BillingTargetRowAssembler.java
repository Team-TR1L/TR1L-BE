package com.tr1l.billing.adapter.out.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.adapter.out.persistence.dto.BillingTargetRow;
import com.tr1l.billing.domain.model.enums.WelfareType;
import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.domain.service.BillingCalculationInput;
import com.tr1l.billing.domain.service.BillingCalculator;
import com.tr1l.billing.error.BillingErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BillingTargetRow(RDB MV 결과 DTO) -> Billing(DRAFT Aggregate) 조립기(Assembler)
 *
 * 핵심 역할:
 * 1) Row에서 "도메인 VO" 생성 (BillingPeriod, CustomerName, CustomerBirthDate, Recipient ...)
 * 2) Row에서 "계산 입력" BillingCalculationInput 생성 (요금/할인/JSONB 파싱/추가과금 계산)
 * 3) BillingCalculator를 호출하여 ChargeLine/DiscountLine/totals 까지 채워진 Billing(DRAFT) 생성
 *
 * 주의:
 * - issue() / Mongo 저장은 Batch Processor/Writer(Application)에서 수행
 */
public final class BillingTargetRowAssembler {

    private final ObjectMapper objectMapper;
    private final BillingCalculator calculator;

    public BillingTargetRowAssembler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.calculator = new BillingCalculator();
    }

    /**
     * Step2 Processor에서 1 row를 받아 Billing(DRAFT)를 만든다.
     *
     * 입력:
     * - billingId/customerId/idempotencyKey: 배치/애플리케이션이 생성/결정한 식별자
     * - row: MV에서 읽은 1건(고객 1명에 대한 청구 입력 스냅샷)
     * - lineIdProvider: ChargeLine/DiscountLine 식별자 생성기
     *
     * 출력:
     * - ChargeLine/DiscountLine/totals까지 계산된 Billing(DRAFT)
     */
    public Billing toDraftBilling(
            BillingId billingId,
            CustomerId customerId,
            IdempotencyKey idempotencyKey,
            BillingTargetRow row,
            BillingCalculator.LineIdProvider lineIdProvider
    ) {
        Objects.requireNonNull(billingId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(row);
        Objects.requireNonNull(lineIdProvider);

        // 1) Row -> Aggregate 생성에 필요한 VO (스냅샷)
        BillingPeriod period = new BillingPeriod(parseBillingMonth(row.billingMonth()));
        CustomerName customerName = new CustomerName(row.userName());
        CustomerBirthDate customerBirthDate = new CustomerBirthDate(parseBirthYearMonth(row.userBirthDate()));
        Recipient recipient = toRecipient(row.recipientEmailEnc(), row.recipientPhoneEnc());

        // 2) Row -> 도메인 계산 입력
        BillingCalculationInput in = toCalculationInput(row);

        // 3) 계산기 호출: 라인 구성 + 할인 적용 + totals 재계산까지 완료된 Billing(DRAFT) 반환
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
     * Row -> BillingCalculationInput 변환(=계산 전용 입력 매핑)
     * - RDB 값/코드/JSONB를 도메인 정책들이 사용할 수 있는 형태로 정규화
     */
    private BillingCalculationInput toCalculationInput(BillingTargetRow row) {
        Money planP = new Money(row.planMonthlyPrice());     // 요금제 정가 P
        Money usageM = computeAdditionalUsageFee(row);       // 추가 사용량 과금 M -> 0 or 1200원

        // 할인율(0~1)
        Rate contractRate = toRate(row.contractRate()); // 0.25
        Rate soldierRate = toRate(row.soldierRate()); // 0.2

        // 복지 값은 welfareEligible 일 때만 세팅
        WelfareType welfareTypeOrNull = null; // 복지 유형
        Rate welfareRateOrNull = null; // 복지 할인률
        Money welfareCapOrNull = null; // 복지 상한선

        if (row.welfareEligible()) { // 복지 유저일 경우
            welfareTypeOrNull = toWelfareType(row.welfareCode()); // 복지코드 -> 변환 필요함
            welfareRateOrNull = toRate(row.welfareRate()); // 할인률 -> 0.35

            // cap: null 또는 0이면 상한 없음(null로 표현)
            if (row.welfareCapAmount() != null && row.welfareCapAmount() > 0) {
                welfareCapOrNull = new Money(row.welfareCapAmount());
            }
        }

        // 결합 할인(정액): 없으면 0원
        Money bundleDiscountAmount = Money.zero();
        if (row.bundleEligible() // 결합 유저면
                && row.bundleTotalDiscountAmount() != null // 할인된 금액
                && row.bundleTotalDiscountAmount() > 0) {
            bundleDiscountAmount = new Money(row.bundleTotalDiscountAmount());
        }

        // JSONB: [{name, monthlyPrice}] -> AddonLine 리스트
        List<BillingCalculationInput.AddonLine> addonLines = parseAddonLines(row.optionsJsonb());

        return new BillingCalculationInput(
                planP,
                usageM,
                row.hasContract(),
                contractRate,
                row.soldierEligible(),
                soldierRate,
                row.welfareEligible(),
                welfareTypeOrNull,
                welfareRateOrNull,
                welfareCapOrNull,
                bundleDiscountAmount,
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
     * perMb는 BigDecimal(예: 0.275원) 이므로 최종 원 단위로 내림(FLOOR)
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
                BigDecimal perMb = row.excessChargePerMb(); // 0.275

                if (included == null) { // DB에서 잘못들어옴
                     throw new BillingDomainException(BillingErrorCode.INVALID_INCLUDED_DATA);
                }

                if (perMb == null) { // DB에서 잘못들어옴
                    throw new BillingDomainException(BillingErrorCode.INVALID_EXCESS_CHARGE_PER_MB);
                }

                long used = row.usedDataMb(); // 총 사용량
                long excessMb = Math.max(0, used - included); // 기본 제공량보다 높을 경우에만 과금 부여

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
     * welfareCode(DB) -> WelfareType(도메인 enum) 매핑
     * - 너가 “일반/기초연금 제외”한다고 했으니 여기서는 WLF-02/03/04만 허용
     */
    private WelfareType toWelfareType(String welfareCode) {
        if (welfareCode == null || welfareCode.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_WELFARE);
        }
        return switch (welfareCode) {
            case "WLF-02" -> WelfareType.DISABLED;
            case "WLF-03" -> WelfareType.NATIONAL_MERIT;
            case "WLF-04" -> WelfareType.BASIC_LIVELIHOOD;
            default -> throw new BillingDomainException(BillingErrorCode.INVALID_WELFARE);
        };
    }


    /**
     * optionsJsonb 원문(JSONB)을 파싱해서 AddonLine 리스트로 만든다.
     *
     * JSONB 포맷(통일):
     * [
     *   { "name":"디즈니+", "monthlyPrice":9405 },
     *   { "name":"티빙",   "monthlyPrice":4950 }
     * ]
     *
     * 현재 BillingCalculationInput.AddonLine 시그니처가 더 많은 필드를 요구하므로
     * MVP용으로:
     * - addonProductId: 1부터 순번
     * - quantity: 1
     * - unitPrice == lineAmount == monthlyPrice
     */
    private List<BillingCalculationInput.AddonLine> parseAddonLines(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) return List.of();

        try {
            List<AddonJson> list = objectMapper.readValue(jsonb, new TypeReference<List<AddonJson>>() {});
            AtomicLong seq = new AtomicLong(1);

            return list.stream()
                    .map(a -> new BillingCalculationInput.AddonLine(
                            seq.getAndIncrement(), // id
                            a.name(), // 부가서비스 이름
                            Quantity.one(), // 1
                            new Money(a.monthlyPrice()), // 왜 2번?
                            new Money(a.monthlyPrice()) //  왜 2번?
                    ))
                    .toList();

        } catch (Exception e) {
            throw new BillingDomainException(BillingErrorCode.INVALID_ADDON);
        }
    }

    /** JSONB 한 항목 포맷 */
    private record AddonJson(String name, long monthlyPrice) {}

    /**
     * "2026-01" -> YearMonth
     */
    private YearMonth parseBillingMonth(String billingMonth) {
        if (billingMonth == null || billingMonth.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_PERIOD);
        }
        return YearMonth.parse(billingMonth);
    }

    /**
     * "1999-03-01" -> YearMonth(1999-03)
     * - CustomerBirthDate가 YearMonth를 받는다고 했으니 월까지만 스냅샷으로 저장
     */
    private YearMonth parseBirthYearMonth(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_USER_BIRTH_DATE);
        }
        LocalDate d = LocalDate.parse(birthDate);
        return YearMonth.of(d.getYear(), d.getMonth());
    }

    /**
     * 암호화된 이메일/폰 문자열을 Recipient VO로 변환
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

package com.tr1l.billing.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingTargetFlatRow(
        //정산 기준월
        LocalDate billingMonth,
        //유저 아이디
        long userId,
        //유저 이름
        String userName,
        //유저 생년월일
        LocalDate userBirthDate,
        //암호화된 유저 이메일
        String recipientEmail,
        //암호화된 유저 핸드폰
        String recipientPhone,
        //요금제 이름
        String planName,
        //요금제 가격
        long planMonthlyPrice,
        //네트워크 종류 이름
        String networkTypeName,
        //데이터 추가 종류 코드
        String dataBillingTypeCode,
        //데이터 추가 종류 이름
        String dataBillingTypeName,
        //요금제에 포함된 데이터
        long includedDataMb,
        //초과된 데이터 추가 비용
        BigDecimal excessChargePerMb,
        //유저가 사용한 총 데이터
        long usedDataMb,
        //선택 약정 유무
        boolean hasContract,
        //선택 약정 할인율
        BigDecimal contractRate,
        //선택약정 기간
        int contractDurationMonths,
        //군인 유무
        boolean soldierEligible,
        //복지 혜택 유무
        boolean welfareEligible,
        //복지 혜택 코드
        String welfareCode,
        //복지 혜택 이름
        String welfareName,
        //복지 혜택 할인율
        BigDecimal welfareRate,
        //복지 헤택 상한 할인
        long welfareCapAmount,
        //부가 서비스 이용 정보
        String optionsJson, // json string
        //발송 제한 시간 시작
        String fromTime,
        //발송 제한 시간 끝
        String toTime,
        //발송 날짜
        String dayTime,
        //발송 채널
        String sendOptionJson
) {}

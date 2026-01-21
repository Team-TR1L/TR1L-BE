package com.tr1l.billing.application.model;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/*==========================
 * step01 호출 시 필요한 파라미터 레코드
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 18.]
 * @version 1.0
 *==========================*/
@Slf4j
public record BillingTargetFlatParams(
        String yearMonth,//정산 기준 달 - ex. 2022-12(2023년 1월 5일에 정산 집계 돌아감)
        LocalDate startDate,
        LocalDate endDate,
        LocalDate billingMonth,
        List<String> channelOrder
) {
    public static BillingTargetFlatParams of(String yearMonth,String channelOrder){
        YearMonth ym=YearMonth.parse(yearMonth);

        LocalDate startDate=ym.atDay(1); //월 1일
        LocalDate endDate=ym.atEndOfMonth();        //월 말일
        log.warn("BillingTargetFlatParams : ym = {} channelOrder = {}",ym,channelOrder);

        List<String> channels = Arrays.stream(channelOrder.split(","))
              .map(String::trim)
              .toList();

        return new BillingTargetFlatParams(yearMonth,startDate,endDate,startDate,channels);
    }
}

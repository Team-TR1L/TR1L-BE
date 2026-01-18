package com.tr1l.billing.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
/*==========================
 * billing_targets Table의 NOT NULL 값등 기본 값만 받는 Projection Record
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 19.]
 * @version 1.0
 *==========================*/
public record BillingTargetBaseRow(
        long userId,
        String userName,
        LocalDate userBirthDate,
        String recipientPhone,
        String recipientEmail,

        //plan
        String planName,
        long planMonthlyPrice,
        String networkTypeName,

        //data
        String dataBillingTypeCode,
        String dataBillingTypeName,
        long includedDataMb,
        BigDecimal excessChargePerMb,

        //welfare
        String welfareCode, //nullable
        String welfareName,
        BigDecimal welfareRate,
        long welfareCapAmount

) {
}

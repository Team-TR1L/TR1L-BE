package com.tr1l.billing.application.model;
/*==========================
 * 부가 서비스 조회 결과용 Row
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 18.]
 * @version 1.0
 *==========================*/
public record OptionItemRow(
        long userId,
        String optionServiceCode,
        String optionServiceName,
        long monthlyPrice
) {
}

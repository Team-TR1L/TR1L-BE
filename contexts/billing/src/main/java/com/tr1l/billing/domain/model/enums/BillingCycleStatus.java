package com.tr1l.billing.domain.model.enums;

/*==========================
 *  청구서 배치 작업 상태
 *
 * 배치 작업 상태 기록을 위한 ENUM
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 17.]
 * @version 1.0
 *==========================*/
public enum BillingCycleStatus {
    RUNNING,
    FINISHED;

    public static BillingCycleStatus from(String value){
        return BillingCycleStatus.from(value);
    }

    public static String to(BillingCycleStatus value){
        return value.name();
    }
}

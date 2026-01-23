package com.tr1l.billing.api.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tr1l.billing.adapter.out.persistence.model.BillingTargetBaseRow;
import com.tr1l.billing.adapter.out.persistence.model.BillingTargetFlatParams;

import java.util.List;

/*==========================
 * Step1 Flatten 유즈케이스(입력 경계)
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 19.]
 * @version 1.0
 *==========================*/
public interface FlattenBillingTargetsUseCase {
    void execute(List<BillingTargetBaseRow> baseRows, BillingTargetFlatParams params) throws JsonProcessingException;
}

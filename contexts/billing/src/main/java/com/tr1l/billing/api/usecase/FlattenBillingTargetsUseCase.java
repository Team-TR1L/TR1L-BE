package com.tr1l.billing.api.usecase;

import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.billing.application.model.BillingTargetFlatRow;
import com.tr1l.billing.application.port.out.BillingTargetSinkPort;
import com.tr1l.billing.application.port.out.BillingTargetSourcePort;
import com.tr1l.billing.application.service.OptionJsonAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/*==========================
 * Step1 Flatten 유즈케이스(입력 경계)
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 19.]
 * @version 1.0
 *==========================*/
public interface FlattenBillingTargetsUseCase {
    void execute(List<BillingTargetBaseRow> baseRows, BillingTargetFlatParams params);
}

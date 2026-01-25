package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.model.WorkAndTargetRow;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import com.tr1l.billing.application.service.IssueBillingService;
import com.tr1l.billing.domain.model.aggregate.Billing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.util.Objects;

/**
 * Processor: BillingTargetRow-> Billing ISSUED 생성까지의 책임
 */
@Slf4j
public class CalculateBillingProcessor implements ItemProcessor<WorkAndTargetRow, CalculateBillingProcessor.Result> {

    private final IssueBillingService issueBillingService;

    public CalculateBillingProcessor(IssueBillingService issueBillingService) {
        this.issueBillingService = issueBillingService;
    }

    // mongDB의 상태 값 + 계산에 필요한 모든 데이터
    @Override
    public Result process(WorkAndTargetRow item) {
        WorkDocClaimPort.ClaimedWorkDoc work = item.work();
        BillingTargetRow row = item.row();

        // row가 없을 경우 실패 처리
        if (row == null) {
            return Result.fail(
                    work,
                    "BATCH-TARGET-NOT-FOUND",
                    "billing_targets row not found"
            );
        }

        // Billing 생성하고 DB에 적재하지는 않음, 계산만의 책임을 가짐
        try {
            var r = issueBillingService.issueOnly(row, work.id());
            Billing billing = r.billing();
            return Result.success(work, billing);
        } catch (Exception e) {
            return Result.fail(work, "BATCH-DOMAIN-ERROR", safeMsg(e));
        }
    }

    private String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 300 ? m.substring(0, 300) : m;
    }


    /**
     * Writer에 넘길  타입
     * - success: billing != null
     * - fail: errorCode/errorMessage 세팅
     */
    public record Result(
            WorkDocClaimPort.ClaimedWorkDoc work,
            Billing billing,            // success일 때만 non-null
            String errorCode,           // fail일 때만 non-null
            String errorMessage
    ) {
        public Result {
            Objects.requireNonNull(work);
        }

        // 계산 성공 시
        public static Result success(WorkDocClaimPort.ClaimedWorkDoc work, Billing billing) {
            return new Result(work, Objects.requireNonNull(billing), null, null);
        }

        // 계산 실패 시
        public static Result fail(WorkDocClaimPort.ClaimedWorkDoc work, String code, String msg) {
            return new Result(work, null, code, msg);
        }

        public boolean isSuccess() {
            return billing != null;
        }
    }
}

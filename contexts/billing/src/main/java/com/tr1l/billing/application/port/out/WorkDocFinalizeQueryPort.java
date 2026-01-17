package com.tr1l.billing.application.port.out;

public interface WorkDocFinalizeQueryPort {

    /**
     * Step4(Finalize)에서 쓰는 집계
     * Pending = Target + Processing
     * nonstop
     * 2026-01-18
     * */

    FinalizeCheckResult countForFinalize(String billingMonth);
    record FinalizeCheckResult(long pending, long failed){}
}

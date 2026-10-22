package com.tr1l.worker.batch.fault;

// Job1 장애 주입 지점
// 재실행 검증에서만 켠다. 운영 설정에는 넣지 않는다
public enum Job1FaultPoint {
    // Step0 billing_cycle 확인과 갱신 직후
    P0,
    // Step1 billing_targets chunk 적재 직후
    P1,
    // Step2 billing_work chunk 적재 직후
    P2,
    // Step3 work claim 직후, 계산 전
    P3a,
    // Step3 Mongo snapshot 저장 전
    P3b,
    // Step3 Mongo snapshot 저장 직후, 상태 갱신 전
    P3c,
    // Step3 상태 갱신 직후, chunk commit 전
    P3d,
    // Step4 billing_cycle 마감 전
    P4
}

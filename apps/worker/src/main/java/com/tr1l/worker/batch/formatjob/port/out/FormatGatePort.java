package com.tr1l.worker.batch.formatjob.port.out;

import com.tr1l.billing.api.usecase.GateBillingCycleUseCase;

import java.time.LocalDate;

import static com.tr1l.billing.api.usecase.GateBillingCycleUseCase.*;

/**==========================
 * Job2 의 step1 을 위한 port
 *
 * @author nonstop
 * @version 1.0.0
 * @date 2026-01-18
 * ========================== */

public interface FormatGatePort {

    // Job1 완료 여부 (billing_cycle)
    String findBillingCycleStatus(LocalDate billingMonth);

    /**
     * billing_format_run에 이번 Job2 실행을 "선점"한다.
     *
     * <p>선점이란?</p>
     * <ul>
     *   <li>동일 (billingMonth, policyVersion, policyIndex) 실행이 동시에 여러 개 떠도,
     *       오직 하나만 RUNNING으로 만들고 나머지는 NOOP 처리할 수 있도록 하는 것</li>
     * </ul>
     *
     * <p>정책/채널 결정</p>
     * <ul>
     *   <li>channelType은 policyOrder.split(",")[policyIndex] 결과로 계산된 currentChannel을 전달한다.</li>
     *   <li>선점 성공 시 DB에 policyOrder/channelType을 저장하여 이후 Step들이 결정론적으로 동작하도록 한다.</li>
     * </ul>
     *
     * @param billingMonth  월 식별자(LocalDate). yyyy-MM의 1일 값 권장
     * @param policyVersion 템플릿/정책 버전
     * @param policyOrder   채널 순서 문자열 (예: "EMAIL,KAKAO,SMS")
     * @param policyIndex   이번 실행에서 사용할 채널 인덱스 (0..N-1)
     * @param channelType   이번 실행의 currentChannel (예: EMAIL)
     * @return FormatGateResult: 선점 성공/이미 실행중/이미 완료/파라미터 불일치 등을 구분하기 위한 결과
     */
    FormatGateResult tryStartFormatRun(LocalDate billingMonth, String policyVersion, String policyOrder, int policyIndex, String channelType);


    /**
     * Step01(Gate) 결과 모델.
     *
     * <p>Tasklet은 이 결과의 decision을 보고 ExitStatus를 결정한다.</p>
     * <ul>
     *   <li>STARTED -> Step02 진행</li>
     *   <li>NOOP_*  -> ExitStatus.NOOP (멱등/중복 실행 차단)</li>
     *   <li>FAIL_*  -> ExitStatus.FAILED (운영 사고 방지)</li>
     * </ul>
     */

    record FormatGateResult(
            Decision decision,
            String storedStatus,
            String storedPolicyOrder,
            String storedChannelType
    ){
        public enum Decision {
            STARTED,
            NOOP_NOT_READY,        // Job1 미완료(참고: 이건 tryStart 전에 걸러도 됨)
            NOOP_ALREADY_DONE,
            NOOP_ALREADY_RUNNING,
            FAIL_PARAM_MISMATCH
        }
    }

}

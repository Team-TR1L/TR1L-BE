package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.WorkDocReleasePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import java.time.Instant;
import java.time.YearMonth;

/*==========================
 * Step3 partition 실패 시 선점 반환
 *
 * - reader 는 fetch-size 만큼 claim 하고 chunk 는 그보다 작아서,
 *   첫 chunk 가 commit 될 때 다음 chunk 몫의 PROCESSING 까지 같이 commit 된다
 * - 다음 chunk 가 실패하면 그 work 는 lease 가 끝날 때까지 PROCESSING 으로 남아 재실행이 가져가지 못한다
 * - partition 이 실패로 끝나면 그 partition(workerId) 이 claim 한 PROCESSING 을 TARGET 으로 돌려준다
 * - 프로세스가 강제 종료되면 이 listener 는 돌지 못한다. 그때는 lease 만료와 Step4 마감 확인이 막는다
 *==========================*/
@Slf4j
public class ReleaseClaimedWorkOnFailureListener implements StepExecutionListener {
    private final WorkDocReleasePort releasePort;

    public ReleaseClaimedWorkOnFailureListener(WorkDocReleasePort releasePort) {
        this.releasePort = releasePort;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() != BatchStatus.FAILED) {
            return null;
        }

        String workerId = stepExecution.getExecutionContext().getString("workerId", null);
        String billingYearMonth = stepExecution.getJobExecution().getExecutionContext().getString("billingYearMonth", null);
        if (workerId == null || billingYearMonth == null) {
            return null;
        }

        int released = releasePort.releaseClaimed(YearMonth.parse(billingYearMonth).atDay(1), workerId, Instant.now());
        log.warn("step3.release workerId={} billingMonth={} released={}", workerId, billingYearMonth, released);
        return null;
    }
}

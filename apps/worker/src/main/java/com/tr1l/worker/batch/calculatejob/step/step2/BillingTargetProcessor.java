package com.tr1l.worker.batch.calculatejob.step.step2;

import com.tr1l.worker.batch.calculatejob.model.BillingTargetKey;
import com.tr1l.worker.batch.calculatejob.model.WorkDoc;
import org.springframework.batch.item.ItemProcessor;
import java.time.Instant;


public class BillingTargetProcessor implements ItemProcessor<BillingTargetKey, WorkDoc> {
    /**
     * @param item 변환에 필요한 아이템
     * @return Read단계에서 받은 item을 몽고디비에 적재할 수 있도록 processor 단계에서 처리한다.
     *
     */
    @Override
    public WorkDoc process(BillingTargetKey item) throws Exception {
        //insert, update 시간 측정해서 넣어준다.
        Instant now = Instant.now();


        String id = item.billingMonth() + ":" + item.userId();
        return new WorkDoc(
                id,
                item.billingMonth(),
                item.userId(),
                "TARGET",
                0,
                now,
                now
        );
    }
}

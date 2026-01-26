package com.tr1l.worker.batch.calculatejob.step.step2;


import com.tr1l.billing.application.command.BillingWorkEnqueueCommand;
import com.tr1l.billing.application.port.out.BillingWorkUpsertPort;
import com.tr1l.worker.batch.calculatejob.model.BillingTargetKey;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Writer 단계  port 이용해서 DB 주입받고 요구사항대로 테이블 만들어 보낸다.
@RequiredArgsConstructor
public class BillingTargetWriter implements ItemWriter<BillingTargetKey> {

    private final BillingWorkUpsertPort billingWorkUpsertPort;


    /**
     * Process the supplied data element. Will not be called with any null items in normal
     * operation.
     *
     * @param chunk of items to be written. Must not be {@code null}.
     * @throws Exception if there are errors. The framework will catch the exception and
     *                   convert or rethrow it as appropriate.
     */
    @Override
    public void write(Chunk<? extends BillingTargetKey> chunk) throws Exception {
        if (chunk == null || chunk.isEmpty()) return;

        Instant now = Instant.now();

        List<BillingWorkEnqueueCommand> commands = chunk.getItems().stream()
                .map(k -> new BillingWorkEnqueueCommand(k.billingMonthDay(), k.userId()))
                .toList();

        billingWorkUpsertPort.bulkUpsertOnInsert(commands, now);
    }
}

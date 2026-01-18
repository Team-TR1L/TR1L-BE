package com.tr1l.worker.batch.calculatejob.step.step2;

import com.tr1l.billing.application.port.out.WorkDocUpsertCommand;
import com.tr1l.billing.application.port.out.WorkDocUpsertPort;
import com.tr1l.worker.batch.calculatejob.model.WorkDoc;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Writer 단계  port 이용해서 DB 주입받고 요구사항대로 테이블 만들어 보낸다.

public class BillingTargetWriter implements ItemWriter<WorkDoc> {

    private final WorkDocUpsertPort workDocUpsertPort;

    public BillingTargetWriter(WorkDocUpsertPort workDocUpsertPort) {
        this.workDocUpsertPort = workDocUpsertPort;
    }

    @Override
    public void write(Chunk<? extends WorkDoc> chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        Instant now = Instant.now();
        List<WorkDocUpsertCommand> commands = new ArrayList<>(chunk.size());

        for (WorkDoc doc : chunk) {
            commands.add(new WorkDocUpsertCommand(
                    doc.id(),
                    doc.billingMonth(),
                    doc.userId(),
                    String.valueOf(doc.status()), // status가 enum이면 toString/ name() 맞춰
                    doc.attemptCount(),
                    doc.createdAt()
            ));
        }

        workDocUpsertPort.bulkUpsertOnInsert(commands, now);
    }
}

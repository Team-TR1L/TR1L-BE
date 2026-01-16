package com.tr1l.worker.batch.calculatejob.step.step2;

import com.tr1l.billing.application.port.out.WorkDocUpsertCommand;
import com.tr1l.billing.application.port.out.WorkDocUpsertPort;
import com.tr1l.worker.batch.calculatejob.model.Step2WorkDoc;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Step2Writer implements ItemWriter<Step2WorkDoc> {

    private final WorkDocUpsertPort workDocUpsertPort;

    public Step2Writer(WorkDocUpsertPort workDocUpsertPort) {
        this.workDocUpsertPort = workDocUpsertPort;
    }

    @Override
    public void write(Chunk<? extends Step2WorkDoc> chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        Instant now = Instant.now();
        List<WorkDocUpsertCommand> commands = new ArrayList<>(chunk.size());

        for (Step2WorkDoc doc : chunk) {
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

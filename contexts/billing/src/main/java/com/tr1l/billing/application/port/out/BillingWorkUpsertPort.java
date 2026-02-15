package com.tr1l.billing.application.port.out;

import com.tr1l.billing.application.command.BillingWorkEnqueueCommand;
import java.time.Instant;
import java.util.List;

/**
  step 2 에 필요한 포트와 메서드 구현
 * ========================== */
public interface BillingWorkUpsertPort {
    void bulkUpsertOnInsert(List<BillingWorkEnqueueCommand> commands , Instant now);
}

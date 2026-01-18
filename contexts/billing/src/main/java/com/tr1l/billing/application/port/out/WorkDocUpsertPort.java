package com.tr1l.billing.application.port.out;

import java.time.Instant;
import java.util.List;

/**
  step 2 에 필요한 포트와 메서드 구현
 * ========================== */
public interface WorkDocUpsertPort {
    void bulkUpsertOnInsert(List<WorkDocUpsertCommand> commands , Instant now);
}

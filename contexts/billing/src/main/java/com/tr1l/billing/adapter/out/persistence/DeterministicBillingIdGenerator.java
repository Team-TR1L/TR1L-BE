package com.tr1l.billing.adapter.out.persistence;

import com.tr1l.billing.application.port.out.BillingIdGenerator;
import com.tr1l.billing.domain.model.vo.BillingId;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class DeterministicBillingIdGenerator implements BillingIdGenerator {

    @Override
    public BillingId generateForWork(String workId) {
        UUID uuid = UUID.nameUUIDFromBytes(("BILLING:" + workId).getBytes(StandardCharsets.UTF_8));
        return new BillingId(uuid.toString());
    }
}
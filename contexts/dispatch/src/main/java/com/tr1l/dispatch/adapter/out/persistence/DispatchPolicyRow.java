package com.tr1l.dispatch.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@AllArgsConstructor
@Getter
public class DispatchPolicyRow {
    public long id;
    public long adminId;
    public String status;
    public int version;

    public String routingPolicyJson;
    public int maxAttemptCount;

    public Instant createdAt;
    public Instant activatedAt;
    public Instant retiredAt;

}
package com.tr1l.apiserver.dto;

import java.util.Map;

public record ManualRunEventDetail(
    String target,      // "BATCH" | "DISPATCH"
    String requestId,   // UUID
    String requestedBy, // admin id/email
    Map<String, Object> parameters
) {}
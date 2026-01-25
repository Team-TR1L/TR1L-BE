package com.tr1l.apiserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.apiserver.dto.ManualRunEventDetail;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualRunPublisher {

    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;

    @Value("${tr1l.control.event-bus-name:tr1l-bus}")
    private String eventBusName;

    public String publish(String target, String requestedBy, Map<String, Object> parameters)
        throws JsonProcessingException {
        String requestId = UUID.randomUUID().toString();

        ManualRunEventDetail detailObj = new ManualRunEventDetail(
            target,
            requestId,
            requestedBy,
            parameters == null ? java.util.Collections.emptyMap() : parameters
        );

        String detailJson = objectMapper.writeValueAsString(detailObj);

        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
            .eventBusName(eventBusName)
            .source("tr1l.admin")
            .detailType("MANUAL_RUN_REQUESTED")
            .detail(detailJson)
            .build();

        PutEventsResponse resp = eventBridgeClient.putEvents(
            PutEventsRequest.builder().entries(entry).build()
        );

        if (resp.failedEntryCount() != null && resp.failedEntryCount() > 0) {
            var r = resp.entries().get(0);
            throw new IllegalStateException("PutEvents failed: " + r.errorCode() + " / " + r.errorMessage());
        }

        return requestId;
    }


}

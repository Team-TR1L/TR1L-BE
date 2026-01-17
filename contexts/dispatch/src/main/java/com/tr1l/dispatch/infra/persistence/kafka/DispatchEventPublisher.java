package com.tr1l.dispatch.infra.persistence.kafka;

public interface DispatchEventPublisher {

    void publish(
            Long userId,
            Long DispatchPolicyId,
            Integer attemptCount,
            String encryptedS3Url,
            String destination
    );
}
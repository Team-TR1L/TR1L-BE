package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.DeliveryResultEventPort;
import com.tr1l.delivery.domain.DeliveryResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryResultEventAdapter implements DeliveryResultEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "delivery-result-events-v1";

    @Override
    public void publish(DeliveryResultEvent event) {
        kafkaTemplate.send(TOPIC, event);
    }
}
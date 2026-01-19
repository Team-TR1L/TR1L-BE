package com.tr1l.delivery.infra.adapter.in;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.delivery.application.service.DeliveryService;
import com.tr1l.delivery.domain.DeliveryResultEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryResultListener {

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    // 토픽 이름과 컨슈머 그룹 체크 필요
    @KafkaListener(topics = "${kafka.topic.delivery-result-events}", groupId = "${kafka.group.delivery-result-handler}")
    public void handleResult(ConsumerRecord<String, String> record, Acknowledgment ack) {

        try {
            // JSON 문자열 -> 객체 변환
            DeliveryResultEvent event = objectMapper.readValue(record.value(), DeliveryResultEvent.class);
            // 비즈니스 로직 실행
            deliveryService.processCallback(event.userId(), event.isSuccess());

            ack.acknowledge();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
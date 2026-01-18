package com.tr1l.delivery.infra.adapter.in;

import com.tr1l.delivery.application.service.DeliveryService;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/*
 * Producer로부터 받아온 이벤트를 소모
 */
@Component
@RequiredArgsConstructor
public class DispatchEventListener {

    private final DeliveryService deliveryService;

    // 토픽 이름과 컨슈머 그룹 체크 필요
    @KafkaListener(topics = "${kafka.topic.dispatch-events}", groupId = "${kafka.group.delivery}")
    public void consume(ConsumerRecord<String, DispatchRequestedEvent> record, Acknowledgment ack) {

        DispatchRequestedEvent event = record.value();

        try {
            // 발송 비즈니스 로직 실행
            deliveryService.deliveryProcess(event);

            // 성공 시 오프셋 커밋
            ack.acknowledge();

        } catch (Exception e) {
            throw e;
        }
    }
}
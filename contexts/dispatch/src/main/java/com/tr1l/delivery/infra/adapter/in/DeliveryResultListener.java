package com.tr1l.delivery.infra.adapter.in;

import com.tr1l.delivery.application.service.DeliveryService;
import com.tr1l.delivery.domain.DeliveryResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryResultListener {

    private final DeliveryService deliveryService;

    // 토픽 이름과 컨슈머 그룹 체크 필요
    @KafkaListener(topics = "delivery-result-events-v1", groupId = "delivery-result-handler-group")
    public void handleResult(DeliveryResultEvent event, Acknowledgment ack) {

        try {
            // 결과 이벤트 처리 비즈니스 로직 실행
            deliveryService.processCallback(event.userId(), event.isSuccess());

            ack.acknowledge();

        } catch (Exception e) {
            throw e;
        }
    }
}
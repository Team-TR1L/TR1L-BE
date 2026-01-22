package com.tr1l.delivery.infra.adapter.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.delivery.application.service.DeliveryService;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/*
 * Producer로부터 받아온 이벤트를 소모
 */
@Component
@RequiredArgsConstructor
public class DispatchEventListener {

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    // 토픽 이름과 컨슈머 그룹 체크 필요
    @KafkaListener(topics = "${kafka.topic.dispatch-events}", groupId = "${kafka.group.delivery}")
    @RetryableTopic(backoff = @Backoff(delay = 1000, multiplier = 2))
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            // JSON 문자열 -> 객체 변환
            DispatchRequestedEvent event = objectMapper.readValue(record.value(), DispatchRequestedEvent.class);
            // 발송 비즈니스 로직 실행
            deliveryService.deliveryProcess(event);
            // 성공 시 오프셋 커밋
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 파싱 실패는 재시도 하지 않음
            ack.acknowledge();
        }
    }
}
package com.tr1l.dispatch.infra.kafka;

import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class KafkaDispatchEventPublisher implements DispatchEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.dispatch-events}")
    private String dispatchTopic;

    @Override
    public void publish(
            Long userId,
            LocalDate billingMonth,
            ChannelType channelType,
            String encryptedS3Url,
            String encryptedDestination
    ) {
        DispatchRequestedEvent event = new DispatchRequestedEvent(
                userId,
                billingMonth,
                channelType,
                encryptedS3Url,
                encryptedDestination
        );

        try {
            // 🔹 Kafka 전송 완료까지 블록
            kafkaTemplate.send(dispatchTopic, event).get(); // get() 호출 → Kafka ACK 기다림
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // interrupted 상태 복원
            throw new RuntimeException("Kafka 메시지 발송 중 인터럽트 발생", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka 메시지 발송 실패", e.getCause());
        }
    }

    @Override
    public void flush() {
        kafkaTemplate.flush();
    }
}
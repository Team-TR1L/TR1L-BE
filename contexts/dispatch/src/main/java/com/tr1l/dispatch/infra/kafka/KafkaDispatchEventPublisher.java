package com.tr1l.dispatch.infra.kafka;

import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
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
            String encryptedS3Buket,
            String encryptedS3Key,
            String encryptedDestination
    ) {
        DispatchRequestedEvent event = new DispatchRequestedEvent(
                userId,
                billingMonth,
                channelType,
                encryptedS3Buket,
                encryptedS3Key,
                encryptedDestination
        );

        // KafkaTemplate은 내부적으로 에러 발생 시 재시도(retry) 설정을 따릅니다.
        kafkaTemplate.send(dispatchTopic, event).whenComplete((result, ex) -> {
            if (ex != null) {
                // 비동기 콜백에서 실패 로그를 남깁니다.
                log.error("❌ Kafka 비동기 발송 실패 userId: {}, 에러: {}", userId, ex.getMessage());
            }
        });
    }

    @Override
    public void flush() {
        kafkaTemplate.flush();
    }
}
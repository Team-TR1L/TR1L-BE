package com.tr1l.dispatch.infra.kafka;

import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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

        // 채널 구분 없이 설정된 단일 토픽으로 발송
        kafkaTemplate.send(dispatchTopic, event);
    }
}
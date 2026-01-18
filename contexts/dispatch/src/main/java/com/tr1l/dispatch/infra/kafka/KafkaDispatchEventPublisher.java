package com.tr1l.dispatch.infra.kafka;

import com.tr1l.dispatch.application.port.out.DispatchEventPublisher;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaDispatchEventPublisher implements DispatchEventPublisher {

    private final KafkaTemplate<String, DispatchRequestedEvent> kafkaTemplate;

    @Override
    public void publish(
            Long userId,
            Long dispatchPolicyId,
            ChannelType channelType,
            Integer attemptCount,
            String encryptedS3Url,
            String encryptedDestination
    ) {
        DispatchRequestedEvent event = new DispatchRequestedEvent(
                userId,
                dispatchPolicyId,
                channelType,
                attemptCount,
                encryptedS3Url,
                encryptedDestination
        );

        //매체별로 topic 이름 결정
        String topic = resolveTopic(event.getChannelType());

        //해당 토픽으로 Event 발송
        kafkaTemplate.send(topic, event);
    }

    private String resolveTopic(ChannelType channelType) {
        return switch (channelType) {
            case EMAIL -> "dispatch.email";
            case SMS -> "dispatch.sms";
            case PUSH -> "dispatch.push";
        };
    }
}

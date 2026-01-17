package com.tr1l.dispatch.infra.persistence.entity;

import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.enums.MessageStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "dispatch_message_candidate",
        indexes = {
                @Index(name = "idx_status_available_time", columnList = "status, available_time")
        }
)

public class DispatchMessageCandidateEntity {
    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private MessageStatus status; // READY, PROCESSING

    private Instant availableTime;

    @Enumerated(EnumType.STRING)
    private ChannelType channel;

    private Long policyVersion;

    private Integer attemptCount;

    private String encryptedS3Url;
    private String encryptedDestination; // 이메일 주소, 전화 번호 등
}

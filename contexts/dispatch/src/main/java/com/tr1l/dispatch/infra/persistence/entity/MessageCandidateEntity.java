package com.tr1l.dispatch.infra.persistence.entity;

import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.domain.model.enums.MessageStatus;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(
        name = "dispatch_message_candidate",
        indexes = {
                @Index(name = "idx_status_available_time", columnList = "status, available_time")
        }
)

public class MessageCandidateEntity {
    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private MessageStatus status; // READY, PROCESSING

    private Instant availableTime;

    @Enumerated(EnumType.STRING)
    private ChannelType channel;

    private Long policyVersion; //참조할 policy의 식별자

    private Integer attemptCount;

    private String encryptedS3Url;
    private String encryptedDestination; // 이메일 주소, 전화 번호 등

    public void markProcessing(){
        if (this.status != MessageStatus.READY)
            throw new DispatchDomainException(DispatchErrorCode.MESSAGE_STATUS_ALREADY_PROCESSING);
    }
}

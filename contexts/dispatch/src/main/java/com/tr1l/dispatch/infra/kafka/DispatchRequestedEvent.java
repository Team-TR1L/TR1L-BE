package com.tr1l.dispatch.infra.kafka;

import com.tr1l.dispatch.domain.model.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DispatchRequestedEvent {

    private Long userId;
    private LocalDate billingMonth;
    private ChannelType channelType;
    private String encryptedS3Url;
    private String encryptedDestination;
}
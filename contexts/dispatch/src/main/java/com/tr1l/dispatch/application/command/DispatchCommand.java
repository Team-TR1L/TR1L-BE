package com.tr1l.dispatch.application.command;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

import java.time.LocalDate;

public record DispatchCommand(
        Long userId,
        LocalDate billingMonth,
        ChannelType channelType,
        String encryptedS3Buket,
        String encryptedS3Key,
        String destination
) {
}
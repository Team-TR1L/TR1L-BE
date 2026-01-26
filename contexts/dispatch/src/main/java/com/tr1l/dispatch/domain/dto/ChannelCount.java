package com.tr1l.dispatch.domain.dto;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

public record ChannelCount(
        ChannelType channelType,
        Long count
) {}

package com.tr1l.dispatch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelDistributionDto {

    private String name;   // 예: SMS, EMAIL
    private int value;
}
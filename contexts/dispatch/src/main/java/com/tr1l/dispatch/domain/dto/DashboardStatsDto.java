package com.tr1l.dispatch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {

    private long todaySent;
    private double successRate;
    private double failureRate;
    private List<DailyTrendDto> dailyTrend;
    private List<ChannelDistributionDto> channelDistribution;
}
package com.tr1l.dispatch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyTrendDto {
    private String date;   // 예: "1/20"
    private long sent;
}
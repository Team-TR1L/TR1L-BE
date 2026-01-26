package com.tr1l.dispatch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlyTrendDto {
    private String hour;  // "09", "10" 등 시간 포맷
    private Long count;   // 해당 시간의 전송량

}
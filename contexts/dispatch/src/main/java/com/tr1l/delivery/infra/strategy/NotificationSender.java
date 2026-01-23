package com.tr1l.delivery.infra.strategy;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

// 매체 추가에 대한 확정 가능성을 고려하기 위해 전략 패턴을 사용
public interface NotificationSender {

    // 해당 채널 타입을 지원하는지 확인
    boolean supports(ChannelType channelType);

    // 실제 발송 로직 수행
    void send(String destination, String content);
}
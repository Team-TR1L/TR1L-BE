package com.tr1l.delivery.application.port.out;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

public interface NotificationClientPort {
    /*
     * 외부 채널로 메시지 전송
     * destination 수신처 (이메일/전화번호)
     * content 내용 (S3 등에서 가져온 본문)
     * mediaType 매체 타입
     */
    boolean send(String destination, String content, ChannelType channelType);
}
package com.tr1l.billing.application.channel;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/*==========================
 * 채널명 조립 클래스
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 20.]
 * @version 1.0
 *==========================*/
@Component
public class ChannelRegistry {
    private final Map<String, ChannelHandler> handlers;

    public ChannelRegistry(List<ChannelHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toUnmodifiableMap(ChannelHandler::key, h -> h));
    }

    public ChannelHandler get(String key) {
        return handlers.get(key);
    }
}

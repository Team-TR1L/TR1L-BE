package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;

import java.util.List;

public record ChannelSequence(List<ChannelType> channels) {

    public ChannelSequence(List<ChannelType> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new DispatchDomainException(DispatchErrorCode.CHANNEL_TYPE_NULL);
        }
        // 불변성을 위해 List를 복사해서 저장
        this.channels = List.copyOf(channels);
    }

    public int size(){
        return channels.size();
    }
}

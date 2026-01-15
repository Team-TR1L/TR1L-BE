package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.domain.model.enums.ChannelType;

import java.util.List;

public record ChannelSequence(List<ChannelType> channels) {

    public ChannelSequence(List<ChannelType> channels) {
        if (channels == null || channels.isEmpty()) {
            // TODO: 에러 변경하기
            throw new IllegalArgumentException("Channels cannot be empty");
        }
        // 불변성을 위해 List를 복사해서 저장
        this.channels = List.copyOf(channels);
    }
}

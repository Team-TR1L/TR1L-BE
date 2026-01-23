package com.tr1l.billing.application.channel;

import com.tr1l.billing.adapter.out.persistence.model.ChannelValue;
import com.tr1l.billing.adapter.out.persistence.model.UserContact;

import java.util.Optional;

public interface ChannelHandler {
    String key();
    Optional<ChannelValue> build(UserContact contact);
}

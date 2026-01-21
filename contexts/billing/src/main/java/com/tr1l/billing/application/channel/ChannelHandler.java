package com.tr1l.billing.application.channel;

import com.tr1l.billing.application.model.ChannelValue;
import com.tr1l.billing.application.model.UserContact;

import java.util.Optional;

public interface ChannelHandler {
    String key();
    Optional<ChannelValue> build(UserContact contact);
}

package com.tr1l.billing.application.channel;

import com.tr1l.billing.adapter.out.persistence.model.ChannelValue;
import com.tr1l.billing.adapter.out.persistence.model.UserContact;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SmsChannelHandler implements ChannelHandler {
    @Override
    public String key() {
        return "SMS";
    }

    @Override
    public Optional<ChannelValue> build(UserContact contact) {
        return contact.hasPhone() ? Optional.of(new ChannelValue("SMS", contact.phoneNumber())) : Optional.empty();
    }
}

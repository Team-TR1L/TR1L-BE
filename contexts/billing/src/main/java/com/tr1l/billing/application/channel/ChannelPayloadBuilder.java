package com.tr1l.billing.application.channel;

import com.tr1l.billing.application.exception.BillingApplicationException;
import com.tr1l.billing.adapter.out.persistence.model.ChannelValue;
import com.tr1l.billing.adapter.out.persistence.model.UserContact;
import com.tr1l.billing.error.BillingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ChannelPayloadBuilder {
    private final ChannelRegistry registry;

    public List<ChannelValue> build(List<String> channelOrder, UserContact contact){
        if (channelOrder == null || channelOrder.isEmpty()){
            throw new  BillingApplicationException(BillingErrorCode.MISSING_REQUIRED_CHANNEL_ORDER);
        }

        List<ChannelValue> result = new ArrayList<>();

        for (String rawKey : channelOrder){
            if (rawKey ==null || rawKey.isBlank()) continue;
            ChannelHandler handler=registry.get(rawKey);

            handler.build(contact).ifPresent(result::add);
        }

        return result;
    }
}

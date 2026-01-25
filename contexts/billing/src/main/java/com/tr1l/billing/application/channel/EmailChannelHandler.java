package com.tr1l.billing.application.channel;

import com.tr1l.billing.application.model.ChannelValue;
import com.tr1l.billing.application.model.UserContact;
import org.springframework.stereotype.Component;

import java.util.Optional;

/*==========================
 * Email 발송 핸들러
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 20.]
 * @version 1.0
 *==========================*/
@Component
public class EmailChannelHandler implements ChannelHandler{
    @Override
    public String key() {
        return "EMAIL";
    }

    @Override
    public Optional<ChannelValue> build(UserContact contact) {
        return contact.hasEmail() ? Optional.of(new ChannelValue("EMAIL",contact.email())): Optional.empty();
    }

}

package com.tr1l.delivery.application.port.out;

import com.tr1l.delivery.domain.DeliveryResultEvent;


public interface DeliveryResultEventPort {
    /*
     * 결과 이벤트 발행
     */
    void publish(DeliveryResultEvent event);
}
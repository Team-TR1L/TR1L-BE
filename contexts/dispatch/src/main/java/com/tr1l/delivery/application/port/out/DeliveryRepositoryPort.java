package com.tr1l.delivery.application.port.out;

public interface DeliveryRepositoryPort {

    // 발송 중 처리 (PROCESSING -> SENT)
    int updateStatusToSent(Long userId);

    // 콜백 성공 처리 (SENT -> SUCCEED)
    void updateStatusToSucceed(Long userId);

    // 콜백 실패 처리 (SENT -> FAILED)
    void updateStatusToFailed(Long userId);
}
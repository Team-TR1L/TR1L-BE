package com.tr1l.delivery.application.service;

import com.tr1l.delivery.application.port.out.DecryptionPort;
import com.tr1l.delivery.application.port.out.DeliveryRepositoryPort;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepositoryPort deliveryRepository;
    private final DecryptionPort decryptionPort;
    private final DeliveryWorker deliveryWorker;

    public void deliveryProcess(DispatchRequestedEvent event) {
        Long userId = event.getUserId();

        // 발송 상태로 변경 시도
        int updatedCount = deliveryRepository.updateStatusToSent(userId, event.getBillingMonth());

        log.info("Delivery process has been called for userId={}", userId);

        if (updatedCount == 0) {
            return;
        }

        // 외부 발송
        deliveryWorker.work(event);
    }

    public void processCallback(Long userId, boolean isSuccess, LocalDate billingMonth) {
        if (isSuccess) {
            // 성공 상태로 변경
            deliveryRepository.updateStatusToSucceed(userId, billingMonth);
        } else {
            // 실패 상태로 변경
            deliveryRepository.updateStatusToFailed(userId, billingMonth);
        }
        log.info("발송 완료 UserID: {}, isSuccess: {}", userId, isSuccess);
    }
}
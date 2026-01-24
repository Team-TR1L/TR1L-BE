package com.tr1l.delivery.application.service;

import com.tr1l.delivery.application.port.out.DeliveryRepositoryPort;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import com.tr1l.util.DecryptionTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepositoryPort deliveryRepository;
    private final DeliveryWorker deliveryWorker;

    public void deliveryProcess(DispatchRequestedEvent event) {
        Long userId = event.getUserId();

        // 발송 상태로 변경 시도
        int updatedCount = deliveryRepository.updateStatusToSent(userId, event.getBillingMonth());

        if (updatedCount == 0) {
            log.warn("중복 메세지 발생");
            return;
        }

        log.warn("SENT 업데이트 성공 user_id: {}", userId);

        // 외부 발송
        deliveryWorker.work(event);
    }

    public void processCallback(Long userId, boolean isSuccess, LocalDate billingMonth) {
        if (isSuccess) {
            // 성공 상태로 변경
            deliveryRepository.updateStatusToSucceed(userId, billingMonth);
            log.warn("SUCCEED 업데이트 성공 user_id: {}", userId);
        } else {
            // 실패 상태로 변경
            deliveryRepository.updateStatusToFailed(userId, billingMonth);
            log.warn("FAILED 업데이트 성공 user_id: {}", userId);
        }
    }
}
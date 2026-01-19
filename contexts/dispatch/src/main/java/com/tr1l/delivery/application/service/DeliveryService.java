package com.tr1l.delivery.application.service;

import com.tr1l.delivery.application.port.out.ContentProviderPort;
import com.tr1l.delivery.application.port.out.DecryptionPort;
import com.tr1l.delivery.application.port.out.DeliveryRepositoryPort;
import com.tr1l.delivery.application.port.out.NotificationClientPort;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepositoryPort deliveryRepository;
    private final NotificationClientPort notificationClient;
    private final DecryptionPort decryptionPort;
    private final ContentProviderPort contentProvider;

    public void deliveryProcess(DispatchRequestedEvent event) {
        Long userId = event.getUserId();

        // 발송 상태로 변경 시도
        int updatedCount = deliveryRepository.updateStatusToSent(userId);

        log.info("Delivery process has been called for userId={}", userId);

        if (updatedCount == 0) {
            return;
        }

        // 복호화
        String destination = decryptSafely(event.getEncryptedDestination());
        String contentUrl = decryptSafely(event.getEncryptedS3Url());

        log.info("복호화 성공 destination: {}, contentUrl: {}", destination, contentUrl);
        
        // S3에서 청구서 다운로드
        String realContent = contentProvider.downloadContent(contentUrl);

        log.info("청구서 다운로드 성공 realContent: {}", realContent);

        // 외부 발송
        notificationClient.send(userId, destination, realContent, event.getChannelType());
    }

    private String decryptSafely(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new DispatchDomainException(DispatchErrorCode.ENCRYPTED_TEXT_NULL);
        }
        return decryptionPort.decrypt(cipherText);
    }

    public void processCallback(Long userId, boolean isSuccess) {
        if (isSuccess) {
            // 성공 상태로 변경
            deliveryRepository.updateStatusToSucceed(userId);
        } else {
            // 실패 상태로 변경
            deliveryRepository.updateStatusToFailed(userId);
        }
        log.info("발송 완료 UserID: {}, isSuccess: {}", userId, isSuccess);
    }
}
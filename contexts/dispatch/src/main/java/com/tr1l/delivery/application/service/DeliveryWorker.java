package com.tr1l.delivery.application.service;

import com.tr1l.delivery.application.port.out.ContentProviderPort;
import com.tr1l.delivery.application.port.out.DecryptionPort;
import com.tr1l.delivery.application.port.out.DeliveryResultEventPort;
import com.tr1l.delivery.application.port.out.NotificationClientPort;
import com.tr1l.delivery.domain.DeliveryResultEvent;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
public class DeliveryWorker {

    private final DecryptionPort decryptionPort;
    private final ContentProviderPort contentProvider;
    private final NotificationClientPort notificationClient;
    private final DeliveryResultEventPort eventPort;
    private final Executor notificationExecutor;

    public void work(DispatchRequestedEvent event) {
        // 비동기 처리
        CompletableFuture.runAsync(() -> {
            boolean isSuccess = false;
            try {
//                // 복호화
//                String s3Url = decryptionPort.decrypt(event.getEncryptedS3Url());
//                String destination = decryptionPort.decrypt(event.getEncryptedDestination());
//
//                // S3 다운로드
//                String realContent = contentProvider.downloadContent(s3Url);

                // 1초 대기 발생
                isSuccess = notificationClient.send(event.getEncryptedDestination(), event.getEncryptedS3Url(), event.getChannelType());

            } catch (Exception e) {
                isSuccess = false;
            } finally {
                // 결과 보고 (Result Topic)
                eventPort.publish(new DeliveryResultEvent(event.getUserId(), isSuccess, event.getBillingMonth()));
            }
        }, notificationExecutor);
    }
}
package com.tr1l.delivery.application.service;

import com.tr1l.delivery.application.port.out.ContentProviderPort;
import com.tr1l.delivery.application.port.out.DeliveryResultEventPort;
import com.tr1l.delivery.application.port.out.NotificationClientPort;
import com.tr1l.delivery.domain.DeliveryResultEvent;
import com.tr1l.dispatch.infra.kafka.DispatchRequestedEvent;
import com.tr1l.util.DecryptionTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryWorker {

    private final DecryptionTool decryptionTool;
    private final ContentProviderPort contentProvider;
    private final NotificationClientPort notificationClient;
    private final DeliveryResultEventPort eventPort;
    private final Executor notificationExecutor;

    public void work(DispatchRequestedEvent event) {
        // 비동기 처리
        CompletableFuture.runAsync(() -> {
            boolean isSuccess = true;
            try {
//                // 복호화
//                String s3Url = decryptionPort.decrypt(event.getEncryptedS3Url());
//                String destination = decryptionTool.decrypt(event.getEncryptedDestination());
//
                // S3 다운로드
                String realContent = contentProvider.downloadContent(event.getEncryptedS3Url());
                log.info("S3 조회 성공, realContent:{}", realContent);

                // 1초 대기 발생
                notificationClient.send(event.getEncryptedDestination(), realContent, event.getChannelType());

            } catch (Exception e) {
                // 해당 과정 중에 에러가 발생할 경우 예외를 삼키고 실패(false)로 결과 토픽 발행
                isSuccess = false;
            } finally {
                // 결과 발행 (Result Topic)
                try {
                    eventPort.publish(new DeliveryResultEvent(event.getUserId(), isSuccess, event.getBillingMonth()));
                } catch (Exception e){
                    log.error("결과 이벤트 발행 실패, user_id:{}, billing_month:{}, isSuccess:{}",event.getUserId(),event.getBillingMonth(),isSuccess);
                }
            }
        }, notificationExecutor);
    }
}
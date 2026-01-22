package com.tr1l.billing.application.model;
/**
 * ==========================
 * RenderedMessage
 *
 * Processor Writer에서 쓰이는 레코드
 * Job2의 step1
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */


public record RenderedMessageResult(
        String billingMonth,   // "2026-01-01" (DB키로 쓸거면 이 값)
        String period,// "2026-01" (S3 경로/표시용, 없어도 됨)
        long userId,

        String recipientPhoneEmail,
        String recipientPhone,

        String emailSubject,// optional
        String emailHtml,// EMAIL content
        String smsText// SMS content
) {}

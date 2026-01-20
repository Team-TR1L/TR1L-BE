package com.tr1l.worker.batch.formatjob.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * ==========================
 * BillingSnapshotDoc
 * billing_target에서 요소를 가져와 매핑
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */



public record BillingSnapshotDoc(
        @Id
        String id,

        @Field("billingMonth")
        String billingMonth,

        @Field("userId")
        long userId,

        @Field("status")
        String status,

        @Field("issuedAt")
        Instant issuedAt,

        @Field("recipientEmailEnc")
        String recipientEmailEnc,

        @Field("recipientPhoneEnc")
        String recipientPhoneEnc,

        @Field("payload")
        Payload payload
) {
    public record Payload(
            @Field("period") ValueString period,
            @Field("customerName") ValueString customerName,

            @Field("subtotalAmount") ValueInt subtotalAmount,
            @Field("discountTotalAmount") ValueInt discountTotalAmount,
            @Field("totalAmount") ValueInt totalAmount,

            @Field("chargeLines") List<ChargeLine> chargeLines,
            @Field("discountLines") List<DiscountLine> discountLines
    ) {}

    public record ValueString(@Field("value") String value) {}
    public record ValueInt(@Field("value") Integer value) {}

    public record ChargeLine(
            @Field("name") String name,
            @Field("pricingSnapshot") PricingSnapshot pricingSnapshot
    ) {}

    public record PricingSnapshot(
            @Field("amount") ValueInt amount
    ) {}

    public record DiscountLine(
            @Field("name") String name,
            @Field("discountType") String discountType,
            @Field("discountAmount") ValueInt discountAmount
    ) {}
}
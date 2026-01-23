package com.tr1l.worker.batch.formatjob.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * ==========================
 * BillingSnapshotDoc
 * Job2의 step1
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


        @Field("payload")
        Payload payload,

        @Field("workId")
        String workId
) {
    public record Payload(
            @Field("period") ValueString period,
            @Field("customerName") ValueString customerName,

            @Field("recipient") Recipient recipient,
            @Field("customerBirthDate") ValueString customerBirthDate,
            @Field("subtotalAmount") ValueInt subtotalAmount,
            @Field("discountTotalAmount") ValueInt discountTotalAmount,
            @Field("totalAmount") ValueInt totalAmount,

            @Field("chargeLines") List<ChargeLine> chargeLines,
            @Field("discountLines") List<DiscountLine> discountLines
    ) {
    }
    public record Recipient(
            @Field("email") ValueString email,
            @Field("phone") ValueString phone
    ) {}

    public record ValueString(@Field("value") String value) {
    }

    public record ValueInt(@Field("value") Integer value) {
    }

    public record ChargeLine(
            @Field("name") String name,
            @Field("pricingSnapshot") PricingSnapshot pricingSnapshot
    ) {
    }

    public record PricingSnapshot(
            @Field("amount") ValueInt amount
    ) {
    }

    public record DiscountLine(
            @Field("name") String name,
            @Field("discountType") String discountType,
            @Field("discountAmount") ValueInt discountAmount
    ) {
    }
}
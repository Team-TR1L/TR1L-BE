package com.tr1l.billing.domain.model.entity;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.domain.model.enums.ChargeType;
import com.tr1l.billing.domain.model.vo.LineId;
import com.tr1l.billing.domain.model.vo.PricingSnapshot;
import com.tr1l.billing.domain.model.vo.SourceRef;
import com.tr1l.billing.error.BillingErrorCode;
import lombok.Getter;

@Getter
public final class ChargeLine {

    private final LineId lineId;
    private final ChargeType type;
    private final String displayName;
    private final SourceRef sourceRef;          // 추적용
    private final PricingSnapshot pricingSnapshot;

    public ChargeLine(
            LineId lineId,
            ChargeType type,
            String displayName,
            SourceRef sourceRef,
            PricingSnapshot pricingSnapshot
    ) {
        if (lineId == null) throw new BillingDomainException(BillingErrorCode.INVALID_LINE_ID);
        if (type == null) throw new BillingDomainException(BillingErrorCode.INVALID_CHARGE_TYPE);
        if (displayName == null || displayName.isBlank()) throw new BillingDomainException(BillingErrorCode.INVALID_DISPLAY_NAME);
        if (pricingSnapshot == null) throw new BillingDomainException(BillingErrorCode.INVALID_PRICING_SNAPSHOT);

        this.lineId = lineId;
        this.type = type;
        this.displayName = displayName.trim();
        this.sourceRef = sourceRef;
        this.pricingSnapshot = pricingSnapshot;
    }

    public LineId lineId() { return lineId; }
    public ChargeType type() { return type; }
    public String displayName() { return displayName; }
    public SourceRef sourceRef() { return sourceRef; }
    public PricingSnapshot pricingSnapshot() { return pricingSnapshot; }

    public com.tr1l.billing.domain.model.vo.Money lineAmount() {
        return pricingSnapshot.lineAmount();
    }
}

package com.tr1l.billing.application.service;

/**
 * ==========================
 * RenderBillingMessageService
 * Job2의 step1
 * 마스킹과 포맷을 적용해 렌더링합니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import com.tr1l.billing.api.usecase.RenderBillingMessageUseCase;
import com.tr1l.billing.application.model.BillingMessage;
import com.tr1l.billing.application.model.RenderBillingMessageQuery;
import com.tr1l.billing.application.model.RenderedMessageResult;
import com.tr1l.billing.application.port.out.TemplateRenderPort;
import com.tr1l.util.MaskingUtil;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;


public class RenderBillingMessageService implements RenderBillingMessageUseCase {

    private final TemplateRenderPort templateRenderPort;

    private static final String EMAIL_TEMPLATE = "bill-email";
    private static final String SMS_TEMPLATE   = "bill-sms";

    public RenderBillingMessageService(TemplateRenderPort templateRenderPort) {
        this.templateRenderPort = templateRenderPort;
    }

    @Override
    public RenderedMessageResult render(RenderBillingMessageQuery q) {
        String maskedName  = MaskingUtil.maskingName(q.customerName());
        String maskedEmail = MaskingUtil.maskEmail(q.recipientEmail());
        String maskedPhone = MaskingUtil.maskingPhone(q.recipientPhone());

        String subtotalStr = money(q.subtotalAmount());
        String discountStr = money(q.discountTotalAmount());
        String totalStr    = money(q.totalAmount());

        List<BillingMessage.LineRow> chargeRows =
                q.chargeLines() == null ? List.of()
                        : q.chargeLines().stream()
                        .map(l -> new BillingMessage.LineRow(l.name(), money(l.amount())))
                        .toList();

        List<BillingMessage.LineRow> discountRows =
                q.discountLines() == null ? List.of()
                        : q.discountLines().stream()
                        .map(l -> new BillingMessage.LineRow(l.name(), money(l.amount())))
                        .toList();


        BillingMessage model = new BillingMessage(
                q.period(),
                maskedName,
                maskedEmail,
                maskedPhone,
                subtotalStr,
                discountStr,
                totalStr,
                chargeRows,
                discountRows
        );

        // ===== 5) 제목/본문 생성 =====
        String emailSubject = "TR1L " + q.period() + " 청구서 안내 (총 " + totalStr + "원)";
        String emailHtml = templateRenderPort.renderEmail(EMAIL_TEMPLATE, model);

        String smsText = templateRenderPort.renderText(SMS_TEMPLATE, model);

        // ===== 6) 결과 =====
        return new RenderedMessageResult(
                q.billingMonth(),
                q.period(),
                q.userId(),
                maskedEmail,   // 결과에 노출되는 값은 마스킹된 것
                maskedPhone,
                emailSubject,
                emailHtml,
                smsText
        );
    }

    // 한국식 돈 표기
    private String money(int amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}



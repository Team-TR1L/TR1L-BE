package com.tr1l.billing.adapter.out.message;

/**
 * ==========================
 * TemplateRenderAdapter
 * Job2의 step1
 * 템플릿 렌더링 어뎁터, 향후 정책 추가시 여기서 추가하면된다.
 * 데이터를 받아 이메일 ,문자 메세지를 조립한다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import com.tr1l.billing.application.model.BillingMessage;
import com.tr1l.billing.application.port.out.TemplateRenderPort;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

public class TemplateRenderAdapter implements TemplateRenderPort {

    private final TemplateEngine templateEngine;

    public TemplateRenderAdapter(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String renderEmail(String templateName, BillingMessage model) {
        Context ctx = buildContext(model);
        return templateEngine.process(templateName,ctx);
    }

    @Override
    public String renderText(String templateName, BillingMessage model) {
        Context ctx = buildContext(model);
        return templateEngine.process(templateName,ctx);
    }

    private Context buildContext(BillingMessage model) {
        if(model ==null){
            throw new IllegalArgumentException("BillingMessage must not be null");
        }

        Context ctx = new Context(Locale.KOREA);
        // 템플릿에서 쓰는 변수명과 1:1로 매칭
        ctx.setVariable("period", model.period());
        ctx.setVariable("customerName", model.customerName());

        ctx.setVariable("email", model.email());
        ctx.setVariable("phone", model.phone());

        ctx.setVariable("subtotalAmount", model.subtotalAmount());
        ctx.setVariable("discountTotalAmount", model.discountTotalAmount());
        ctx.setVariable("totalAmount", model.totalAmount());

        ctx.setVariable("chargeLines", model.chargeLines());
        ctx.setVariable("discountLines", model.discountLines());

        return ctx;

    }
}



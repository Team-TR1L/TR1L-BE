package com.tr1l.billing.application.port.out;

/**
 * ==========================
 * TemplateRenderPort
 *
 * Job2 step1 Port
 * 템플릿 렌더링 포트입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import com.tr1l.billing.application.model.BillingMessage;

public interface TemplateRenderPort {
    String renderEmail(String templateName, BillingMessage model);
    String renderText(String templateName, BillingMessage model);
}


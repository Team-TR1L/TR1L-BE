package com.tr1l.worker.config.step;

/**
 * ==========================
 * BillingRenderWiringConfig
 * Job2의 step 1
 * thymeleaf 엔진, port연결
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import com.tr1l.billing.adapter.out.message.TemplateRenderAdapter;
import com.tr1l.billing.api.usecase.RenderBillingMessageUseCase;
import com.tr1l.billing.application.port.out.TemplateRenderPort;
import com.tr1l.billing.application.service.RenderBillingMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;

@Configuration
public class BillingRenderWiringConfig {
    @Bean
    public TemplateRenderPort templateRenderPort(TemplateEngine templateEngine) {
        return new TemplateRenderAdapter(templateEngine);
    }

    @Bean
    public RenderBillingMessageUseCase renderBillingMessageUseCase(TemplateRenderPort templateRenderPort) {
        return new RenderBillingMessageService(templateRenderPort);
    }
}



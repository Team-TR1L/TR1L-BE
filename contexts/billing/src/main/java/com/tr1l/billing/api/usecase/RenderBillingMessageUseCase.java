package com.tr1l.billing.api.usecase;

/**
 * ==========================
 * RenderBillingMessageUseCase
 * Job2의 step1
 * 메시지 렌더링 유스케이스입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

import com.tr1l.billing.application.model.RenderBillingMessageQuery;
import com.tr1l.billing.application.model.RenderedMessageResult;

public interface RenderBillingMessageUseCase {

    RenderedMessageResult render(RenderBillingMessageQuery snapshot);
}




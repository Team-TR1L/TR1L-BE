package com.tr1l.worker.batch.formatjob.step.step1;

import com.tr1l.billing.api.usecase.RenderBillingMessageUseCase;
import com.tr1l.billing.application.model.RenderBillingMessageQuery;
import com.tr1l.util.DecryptionTool;
import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import com.tr1l.billing.application.model.RenderedMessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.context.Context;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * ==========================
 * BillingSnapShotProcessor
 * Job2의 step1
 * Reader에서 데이터들을 읽어 온 후 Email, SMS 서비스에 넘겨준뒤 각 유저 청구서값 writer에 리턴
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */


@Slf4j
@StepScope
public class BillingSnapShotProcessor implements ItemProcessor<BillingSnapshotDoc, RenderedMessageResult> {


    private final RenderBillingMessageUseCase useCase;
    private final DecryptionTool decryptionTool;

    public BillingSnapShotProcessor(RenderBillingMessageUseCase useCase, DecryptionTool decryptionTool) {
        this.useCase = useCase;
        this.decryptionTool = decryptionTool;
    }

    // process 시작
    @Override
    public RenderedMessageResult process(BillingSnapshotDoc doc) throws Exception {
        log.warn("doc = {}", doc.toString());
        if (doc.payload() == null) return null;
        return useCase.render(toQuery(doc));
    }




    private RenderBillingMessageQuery toQuery(BillingSnapshotDoc doc){

        // snapshot 에서 필요한 값을 뽑기

        var p = doc.payload();
        String period = safeStr(p.period()); //기간
        String customerName = safeStr(p.customerName()); //이름

        //복호화
        String recipientEmailEnc = safeStr(p.recipient() != null ? p.recipient().email() : null);
        String recipientPhoneEnc = safeStr(p.recipient() != null ? p.recipient().phone() : null);

        String recipientEmail = decryptionTool.decrypt(recipientEmailEnc);
        String recipientPhone = decryptionTool.decrypt(recipientPhoneEnc);

        //subtotal = 플러스 요금 합  discount = 할인된 요금 총합  total = subtotal - discount 총합
        // (Mongo snapshot에 이미 계산되어 저장된 값을 그대로 사용)
        int subtotal = safeInt(p.subtotalAmount());
        int discountTotal = safeInt(p.discountTotalAmount());
        int total = safeInt(p.totalAmount());

        log.warn("process = {} , {} , {} , {} , {} , {}", p, period, customerName, subtotal, discountTotal, total);

        //청구요금 금액리스트
        List<RenderBillingMessageQuery.Line> chargeLines = new ArrayList<>();
        if (p.chargeLines() != null) {
            for (var c : p.chargeLines()) {
                int amount = (c.pricingSnapshot() != null && c.pricingSnapshot().amount() != null)
                        ? safeInt(c.pricingSnapshot().amount())
                        : 0;
                chargeLines.add(new RenderBillingMessageQuery.Line(c.name(), amount));
            }
        }

        //할인 요금 금액 리스트 없을수도 있으니 널값일때는 continue
        List<RenderBillingMessageQuery.Line> discountLines = new ArrayList<>();

        var src = p.discountLines();
        if (src != null) {
            for (var d : src) {
                if (d == null) continue;

                var amtObj = d.discountAmount();
                if (amtObj == null) continue;

                Integer amount = amtObj.value();
                if (amount == null) continue; // 

                String name = d.name();
                if (name == null || name.isBlank()) continue; // (권장) 이름도 없으면 제외

                discountLines.add(new RenderBillingMessageQuery.Line(name, amount));
            }
        }


        return new RenderBillingMessageQuery(
                doc.billingMonth(),
                doc.userId(),
                period,
                customerName,
                recipientEmail,
                recipientPhone,
                subtotal,
                discountTotal,
                total,
                chargeLines,
                discountLines
        );

    }



    // 한국식 돈 변환
    private String money(int m) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(m);
    }

    // payload 안의 밸류 값 안전하게 꺼내기
    private String safeStr(BillingSnapshotDoc.ValueString v) {
        return (v == null || v.value() == null) ? "" : v.value();
    }

    //payload 안의 숫자값 안전하게 꺼내기
    private int safeInt(BillingSnapshotDoc.ValueInt valueInt) {
        return (valueInt == null || valueInt.value() == null) ? 0 : valueInt.value();
    }
}

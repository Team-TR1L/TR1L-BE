package com.tr1l.worker.batch.formatjob.step.step1;

import com.tr1l.worker.batch.formatjob.domain.BillingSnapshotDoc;
import com.tr1l.worker.batch.formatjob.domain.RenderedMessage;
import org.springframework.batch.item.ItemProcessor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * ==========================
 * BillingSnapShotProcessor
 * Email, SMS 템플릿에 맞게 파일 작성 후 Writer 작성
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-20
 * ==========================
 */



public class BillingSnapShotProcessor implements ItemProcessor<BillingSnapshotDoc, RenderedMessage> {

    //thymleaf 필요한 엔진
    private final TemplateEngine templateEngine;

    public BillingSnapShotProcessor(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public RenderedMessage process(BillingSnapshotDoc doc) throws Exception {
        if (doc.payload() == null) return null;

        // snapshot 에서 필요한 값을 뽑기
        var p = doc.payload();
        String period = safeValue(p.period()); //기간
        String customerName = safeValue(p.customerName()); //이름

        //subtotal = 플러스 요금 합  discount = 할인된 요금 총합  total = subtotal - discount 총합
        // (Mongo snapshot에 이미 계산되어 저장된 값을 그대로 사용)
        int subtotal = safeInt(p.subtotalAmount());
        int discountTotal = safeInt(p.discountTotalAmount());
        int total = safeInt(p.totalAmount());

        //이메일 제목 , 이메일HTML, 문자메세지
        String emailSubject = "TR1L " + period + " 청구서 안내 (총 " + money(total) + "원)";
        String emailHtml = renderEmailHtml(period,customerName,subtotal,discountTotal,total,p);
        String smsText =
                "[TR1L] "+"이름 : "+customerName+" 청구 월 :"+ period + " 청구 금액: " + money(total) + "원\n" +
                        "예금주: nonstop  국민: 111798-468468-1351";


        return new RenderedMessage(
                doc.billingMonth(),
                period,
                doc.userId(),
                doc.recipientEmailEnc(),
                doc.recipientPhoneEnc(),
                emailSubject,
                emailHtml,
                smsText
        );

    }

    /**
     * EMAIL HTML 렌더링 담당 메서드
     * 템플릿에서 th:each 반복문으로 출력할 수 있도록 List로 넣는다.
     */
    private String renderEmailHtml(String period, String customerName, int subtotal, int discountTotal, int total, BillingSnapshotDoc.Payload payload) {

        //  chargeLines-> 템플릿용 리스트로 변환하는단계 서비스 이름과 가격 만 집어 넣는다.
        List<LineRow> chargeRows = new ArrayList<>();
        if (payload.chargeLines() != null) {
            for (var c : payload.chargeLines()) {
                Integer amount = (c.pricingSnapshot() != null && c.pricingSnapshot().amount() != null)
                        ? c.pricingSnapshot().amount().value()
                        : 0;
                // 돈은 , 쉼표 찍어서
                chargeRows.add(new LineRow(c.name(), money(amount)));
            }
        }

        // discountLines -> 할인 목록들 템플릿용 리스트로 변환하는단계 서비스 이름과 가격 만 집어 넣는다.
        List<LineRow> discountRows = new ArrayList<>();
        if (payload.discountLines() != null) {
            for (var d : payload.discountLines()) {
                Integer amount = (d.discountAmount() != null) ? d.discountAmount().value() : 0;
                discountRows.add(new LineRow(d.name(), money(amount)));
            }
        }
        // ===== 3) Thymeleaf Context 구성 =====
        // Locale.KOREA는 숫자 포맷(콤마)과 텍스트 렌더링의 기본 로케일로 사용

        Context ctx = new Context(Locale.KOREA);
        ctx.setVariable("period", period);
        ctx.setVariable("customerName", customerName);

        ctx.setVariable("subtotalAmount", money(subtotal));
        ctx.setVariable("discountTotalAmount", money(discountTotal));
        ctx.setVariable("totalAmount", money(total));

        ctx.setVariable("chargeLines", chargeRows);
        ctx.setVariable("discountLines", discountRows);

        return templateEngine.process("bill-email", ctx);
    }

    // 서비스 이름과 가격
    public record LineRow(String name, String amount) {
    }

    // 한국식 돈 변환
    private String money(int m) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(m);
    }

    // payload 안의 밸류 값 안전하게 꺼내기
    private String safeValue(BillingSnapshotDoc.ValueString valueString) {
        return (valueString == null || valueString.value() == null) ? "" : valueString.value();
    }

    //palyload 안의 숫자값 안전하게 꺼내기
    private int safeInt(BillingSnapshotDoc.ValueInt valueInt) {
        return (valueInt == null || valueInt.value() == null) ? 0 : valueInt.value();
    }
}

package com.tr1l.billing.application.service;

import com.tr1l.billing.api.usecase.FlattenBillingTargetsUseCase;
import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.billing.application.model.BillingTargetFacts;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.billing.application.model.BillingTargetFlatRow;
import com.tr1l.billing.application.port.out.BillingTargetSinkPort;
import com.tr1l.billing.application.port.out.BillingTargetSourcePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlattenBillingTargetService implements FlattenBillingTargetsUseCase {
    private final BillingTargetSourcePort sourcePort;
    private final BillingTargetSinkPort sinkPort;
    private final OptionJsonAssembler optionJsonAssembler;
    private final BillingTargetAssembler targetAssembler;

    //UPSERT를 target DB에 하므로 트랜잭션 처리
    @Transactional(transactionManager = "TX-target")
    @Override
    public void execute(List<BillingTargetBaseRow> baseRows, BillingTargetFlatParams params) {
        if (baseRows == null || baseRows.isEmpty()) return;

        List<Long> userIds = baseRows.stream()
                .map(BillingTargetBaseRow::userId)
                .distinct()
                .toList();

        BillingTargetFacts facts= sourcePort.fetchFacts(userIds,params);

        //부가 서비스 목록 -> Jsonb로 만들기
        Map<Long,String> optionsJson = optionJsonAssembler.toJsonByUser(facts.optionItems());
        //추가 값들 불러오기 - 할인 혜택, 군인 할인, 부가서비스 등
        List<BillingTargetFlatRow> flatRows = targetAssembler.assemble(baseRows,facts,params,optionsJson);

        sinkPort.upsertBillingTargets(flatRows);
    }
}

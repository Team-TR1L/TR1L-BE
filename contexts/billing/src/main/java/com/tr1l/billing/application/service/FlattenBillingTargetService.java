package com.tr1l.billing.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tr1l.billing.api.usecase.FlattenBillingTargetsUseCase;
import com.tr1l.billing.application.model.BillingTargetBaseRow;
import com.tr1l.billing.application.model.BillingTargetFacts;
import com.tr1l.billing.application.model.BillingTargetFlatParams;
import com.tr1l.billing.application.model.BillingTargetFlatRow;
import com.tr1l.billing.application.port.out.BillingTargetMemberPort;
import com.tr1l.billing.application.port.out.BillingTargetSinkPort;
import com.tr1l.billing.application.port.out.BillingTargetSourcePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlattenBillingTargetService implements FlattenBillingTargetsUseCase {
    private final BillingTargetSourcePort sourcePort;
    private final BillingTargetSinkPort sinkPort;
    private final OptionJsonAssembler optionJsonAssembler;
    private final BillingTargetAssembler targetAssembler;
    private final BillingTargetMemberPort memberPort;

    //UPSERT를 target DB에 하므로 트랜잭션 처리
    @Transactional(transactionManager = "TX-target")
    @Override
    public void execute(List<BillingTargetBaseRow> baseRows, BillingTargetFlatParams params) throws JsonProcessingException {
        if (baseRows == null || baseRows.isEmpty()) return;

        // Step0 이 고정한 명단에 있는 사용자만 적재한다. 그 사이 탈퇴했어도 명단에 있으면 청구한다 (TR1L-179)
        Set<Long> members = memberPort.membersAmong(
                YearMonth.parse(params.yearMonth()),
                baseRows.stream().map(BillingTargetBaseRow::userId).toList());
        baseRows = baseRows.stream()
                .filter(row -> members.contains(row.userId()))
                .toList();
        if (baseRows.isEmpty()) return;

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

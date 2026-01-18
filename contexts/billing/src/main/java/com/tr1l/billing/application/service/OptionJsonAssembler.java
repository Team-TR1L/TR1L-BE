package com.tr1l.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.billing.application.exception.BillingApplicationException;
import com.tr1l.billing.application.model.OptionItemRow;
import com.tr1l.billing.error.BillingErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*==========================
 * 부가 서비스 목록 Jsonb로 변환 클래스
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 19.]
 * @version 1.0
 *==========================*/
@Component
public class OptionJsonAssembler {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<Long, String> toJsonByUser(List<OptionItemRow> itemRows) {
        if (itemRows == null || itemRows.isEmpty()) return Map.of();

        //<유저 아이디 , [{"부가서비스 이름" : "넷플릭스"},{"부가 서비스 가격":20000(long)}] 형식
        Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();

        for (OptionItemRow itemRow : itemRows){
            grouped.computeIfAbsent(itemRow.userId(),k->new ArrayList<>());
            grouped.get(itemRow.userId()).add(Map.of(
                    "name",itemRow.optionServiceName(),
                    "monthlyPrice",itemRow.monthlyPrice()
            ));
        }

        Map<Long,String> out = new HashMap<>();

        for (var e : grouped.entrySet()){
            try {
                //List<> -> Json 변한
                out.put(e.getKey(),mapper.writeValueAsString(e.getValue()));
            }catch (Exception ex){
                throw new BillingApplicationException(BillingErrorCode.JSON_PARSING_ERROR);
            }
        }

        return out;
    }
}

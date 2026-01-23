package com.tr1l.billing.adapter.out.persistence.model;

import java.math.BigDecimal;

/*==========================
 * 선택 약정 조회 값 매핑 클래스
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 18.]
 * @version 1.0
 *==========================*/
public record ContractFact(int durationMonths, BigDecimal discountRate) {}

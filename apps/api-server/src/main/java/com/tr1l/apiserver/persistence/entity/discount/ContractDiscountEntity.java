package com.tr1l.apiserver.persistence.entity.discount;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

@Entity
@Table(name = "contract_discount")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("선택 약정 할인 정책")
public class ContractDiscountEntity {
    @Id
    @Column(name = "duration_months", nullable = false, unique = true)
    private Integer durations;

    @Column(
            name = "discount_percent",
            nullable = false,
            precision = 5,
            scale = 4
    )
    private BigDecimal discountPercent;
}

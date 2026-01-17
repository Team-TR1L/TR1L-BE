package com.tr1l.apiserver.persistence.entity.discount;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;


@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Entity
@Table(name = "welfare_discount")
@Comment("복지 할인 정책")
public class WelfareDiscountEntity {
    @Id
    @Column(name = "welfare_code",nullable = false,unique = true,length = 10)
    @Comment("복지 할인 정책")
    private String welfareCode;

    @Column(name = "welfare_name",nullable = false,length = 30)
    @Comment("복지 종류")
    private String welfareName;

    @Column(
        name = "discount_rate",
        nullable = false,
        precision = 5,
        scale = 4
    )
    @Comment("할인율")
    private BigDecimal discountPercent;

    @Column(name = "max_discount")
    @Comment("할인 상한액")
    private Integer maxDiscount;
}

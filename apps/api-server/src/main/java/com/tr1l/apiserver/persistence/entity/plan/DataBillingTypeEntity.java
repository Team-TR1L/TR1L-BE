package com.tr1l.apiserver.persistence.entity.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "data_billing_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("무제한 여부 종류")
public class DataBillingTypeEntity {
    @Id
    @Column(
            name = "data_billing_type_code",
            length = 10,
            nullable = false,
            unique = true
    )
    @Comment("무제한 데이터 코드")
    private String dataTypeCode;

    @Column(name = "data_billing_type_name",length = 50,nullable = false,unique = true)
    private String dataBillingTypeName;
}

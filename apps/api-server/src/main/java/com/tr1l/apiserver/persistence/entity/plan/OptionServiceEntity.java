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
@Table(name = "option_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("부가 서비스 종류")
public class OptionServiceEntity {
    @Id
    @Column(name = "option_service_code",unique = true,nullable = false,length = 10)
    @Comment("부가 서비스 종류 식별자")
    private String optionServiceCode;

    @Column(name = "option_service_name",unique = true,nullable = false,length = 20)
    @Comment("부가 서비스 종류 이름")
    private String optionServiceName;
}

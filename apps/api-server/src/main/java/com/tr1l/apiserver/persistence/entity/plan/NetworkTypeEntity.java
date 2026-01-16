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
@Table(name = "network_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("네트워크 종류")
public class NetworkTypeEntity {
    @Id
    @Column(name = "network_type_code",nullable = false,unique = true,length = 10)
    @Comment("네트워크 종류 코드")
    private String networkCode;

    @Column(name = "network_type_name",nullable = false,length = 10)
    @Comment("네트워크 이름")
    private String networkName;
}

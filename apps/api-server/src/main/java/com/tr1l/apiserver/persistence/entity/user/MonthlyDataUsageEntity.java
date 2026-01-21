package com.tr1l.apiserver.persistence.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.Instant;

@Entity
@Table(name = "monthly_data_usage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_monthly_data_usage_user_month", columnNames = {"user_id", "usage_year_month"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Comment("월별 데이터 사용량")
public class MonthlyDataUsageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_usage_id", nullable = false, unique = true)
    private Long usageId;


    @Column(name = "used_data_mb", nullable = false)
    @Comment("사용 데이터량")
    private Long usedData;

    @Column(name = "usage_aggregated_at", nullable = false)
    @Comment("데이터 사용량 집계일")
    private Instant aggregatedAt;

    @Column(name = "usage_year_month", nullable = false, length = 6)
    private String yearMonth;

    //FK
    @Column(name = "user_id", nullable = false)
    private Long userId;

}

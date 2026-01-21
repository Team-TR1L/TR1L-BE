package com.tr1l.dispatch.infra.persistence.entity;

import com.tr1l.dispatch.domain.model.vo.ChannelRoutingPolicy;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dispatch_policy")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispatchPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "routing_policy_json", columnDefinition = "TEXT", nullable = false)
    private String routingPolicyJson;

    @Column(name = "max_attempt_count", nullable = false)
    private int maxAttemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;
}

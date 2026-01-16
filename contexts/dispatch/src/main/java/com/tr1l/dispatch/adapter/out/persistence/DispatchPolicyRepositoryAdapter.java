package com.tr1l.dispatch.adapter.out.persistence;

import com.tr1l.dispatch.application.port.out.DispatchPolicyRepositoryPort;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DispatchPolicyRepositoryAdapter
        implements DispatchPolicyRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void save(DispatchPolicy policy) {
        DispatchPolicyRow row = DispatchPolicyMapper.toRow(policy);

        jdbc.update("""
            INSERT INTO dispatch_policy
            (id, admin_id, status, version, routing_policy,
             created_at, activated_at, retired_at)
            VALUES (:id, :adminId, :status, :version, :routingPolicy,
                    :createdAt, :activatedAt, :retiredAt)
        """, Map.of(
                "id", row.id,
                "adminId", row.adminId,
                "status", row.status,
                "version", row.version,
                "routingPolicy", row.routingPolicyJson,
                "createdAt", row.createdAt,
                "activatedAt", row.activatedAt,
                "retiredAt", row.retiredAt
        ));
    }
}
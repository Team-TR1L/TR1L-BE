package com.tr1l.apiserver.controller;

import com.tr1l.apiserver.DispatchPolicyRequest;
import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.service.DispatchPolicyService;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/policies")
@RequiredArgsConstructor
public class DispatchPolicyController {

    private final DispatchPolicyService service;

    /** 정책 생성 */
    @PostMapping
    public ResponseEntity<Long> createPolicy(@RequestBody DispatchPolicyRequest request) {
        Long policyId = service.createPolicy(
                new CreateDispatchPolicyCommand(
                        request.getAdminId(),
                        request.getChannelRoutingPolicy()
                )
        );
        return ResponseEntity.ok(policyId);
    }

    /** 모든 정책 조회 */
    @GetMapping
    public ResponseEntity<List<DispatchPolicy>> getAllPolicies() {
        return ResponseEntity.ok(service.findPolicies());
    }

    /** 단일 정책 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<DispatchPolicy> getPolicy(@PathVariable("id") Long policyId) {
        return ResponseEntity.ok(service.findPolicy(policyId));
    }

    /** 정책 폐기 */
    @PatchMapping("/{id}/retire")
    public ResponseEntity<Void> retirePolicy(@PathVariable("id") Long policyId) {
        service.deletePolicy(policyId);
        return ResponseEntity.ok().build();
    }

    /** 정책 수정 (채널 + 상태) */
    @PutMapping("/{id}")
    public ResponseEntity<DispatchPolicy> updatePolicy(
            @PathVariable("id") Long policyId,
            @RequestBody DispatchPolicyRequest request
    ) {
        DispatchPolicy updated = service.updatePolicy(
                policyId,
                request.getChannelRoutingPolicy(),
                request.getStatus()
        );
        return ResponseEntity.ok(updated);
    }

    /** 현재 ACTIVE 정책 조회 */
    @GetMapping("/active")
    public ResponseEntity<DispatchPolicy> getActivePolicy() {
        return ResponseEntity.ok(service.findCurrentActivePolicy());
    }
}


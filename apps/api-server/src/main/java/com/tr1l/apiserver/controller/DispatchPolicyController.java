package com.tr1l.apiserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tr1l.apiserver.dto.DispatchPolicyRequest;
import com.tr1l.apiserver.service.ManualRunPublisher;
import com.tr1l.dispatch.application.command.CreateDispatchPolicyCommand;
import com.tr1l.dispatch.application.service.DispatchPolicyService;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class DispatchPolicyController {

    private final DispatchPolicyService service;
    private final ManualRunPublisher publisher;

    /** 정책 생성 */
    @Operation(summary = "정책 생성", description = "관리자 ID와 채널 라우팅 정책을 받아 새로운 발송 정책을 생성합니다.")
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
    @Operation(summary = "모든 정책 조회", description = "시스템에 등록된 모든 발송 정책 리스트를 조회합니다.")
    @GetMapping
    public ResponseEntity<List<DispatchPolicy>> getAllPolicies() {
        return ResponseEntity.ok(service.findPolicies());
    }

    /** 단일 정책 조회 */
    @Operation(summary = "단일 정책 상세 조회", description = "정책 ID를 통해 특정 정책의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<DispatchPolicy> getPolicy(@PathVariable("id") Long policyId) {
        return ResponseEntity.ok(service.findPolicy(policyId));
    }

    /** 정책 폐기 */
    @Operation(summary = "정책 폐기", description = "특정 정책을 RETIRED 상태로 변경하여 더 이상 사용하지 않도록 합니다.")
    @PatchMapping("/{id}/retire")
    public ResponseEntity<Void> retirePolicy(@PathVariable("id") Long policyId) {
        service.deletePolicy(policyId);
        return ResponseEntity.ok().build();
    }

    /** 정책 생성*/
    @Operation(summary = "정책 활성화", description = "특정 정책을 ACTIVE 상태로 변경합니다.")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activatePolicy(@PathVariable("id") Long policyId) {
        service.activatePolicy(policyId);
        return ResponseEntity.ok().build();
    }

    /** 정책 수정 (채널) */
    @Operation(summary = "정책 수정", description = "정책의 채널 라우팅 정보를 업데이트합니다.")
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
    @Operation(summary = "현재 활성화된 정책 조회", description = "현재 시스템에서 발송에 사용 중인 ACTIVE 상태의 정책을 조회합니다.")
    @GetMapping("/active")
    public ResponseEntity<DispatchPolicy> getActivePolicy() {
        return ResponseEntity.ok(service.findCurrentActivePolicy());
    }

    /** 배치 수동 실행 */
    @Operation(summary = "청구서 제작 배치 수동 실행", description = "청구서 제작 배치를 즉시 수동으로 실행합니다.")
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> startBatch(Principal principal)
        throws JsonProcessingException {
        String requestedBy = principal != null ? principal.getName() : "unknown-admin";
        String requestId = publisher.publish("BATCH", requestedBy, java.util.Collections.emptyMap());

        return ResponseEntity.accepted().body(Map.of(
            "requestId", requestId,
            "status", "REQUESTED"
        ));
    }

    /** 청구서 수동 발송 */
    @Operation(summary = "청구서 수동 발송", description = "청구서 발송을 즉시 수동으로 실행합니다.")
    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> startDispatch(Principal principal)
        throws JsonProcessingException {
        String requestedBy = principal != null ? principal.getName() : "unknown-admin";
        String requestId = publisher.publish("DISPATCH", requestedBy, java.util.Collections.emptyMap());

        return ResponseEntity.accepted().body(Map.of(
            "requestId", requestId,
            "status", "REQUESTED"
        ));
    }
}


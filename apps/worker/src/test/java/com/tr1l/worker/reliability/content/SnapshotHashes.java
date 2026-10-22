package com.tr1l.worker.reliability.content;

import java.util.Map;

// 사용자별 snapshot 해시 묶음
// S-001 기준으로 저장해 두고 재실행 뒤와 비교
public record SnapshotHashes(
        String billingMonthDay,
        Map<Long, String> hashes,
        Map<Long, Integer> duplicateUsers
) {
}

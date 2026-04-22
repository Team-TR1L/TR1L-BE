package com.tr1l.worker.reliability.invariant;

// PR1 허용 체크 타입
public enum InvariantCheckType {
    POSTGRES,
    CROSS_DB;

    public static InvariantCheckType from(String raw) {
        return InvariantCheckType.valueOf(raw.replace('-', '_').toUpperCase());
    }
}

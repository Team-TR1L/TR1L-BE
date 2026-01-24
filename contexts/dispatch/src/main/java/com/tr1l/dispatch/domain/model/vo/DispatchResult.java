package com.tr1l.dispatch.domain.model.vo;

public class DispatchResult {

    private final int success;
    private final int failed;

    public DispatchResult(int success, int failed) {
        this.success = success;
        this.failed = failed;
    }

    public int success() {
        return success;
    }

    public int failed() {
        return failed;
    }
}
package com.tr1l.error;

public interface ErrorCode {
    String code();
    ErrorCategory category();
    String message();
}

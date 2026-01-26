package com.tr1l.worker.batch.formatjob.step.step1;

// 일시적 오류로 재시도 대상인 예외
public class RetryableFormatJobException extends RuntimeException {
    public RetryableFormatJobException(String message, Throwable cause) {
        super(message, cause);
    }
}

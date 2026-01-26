package com.tr1l.worker.batch.formatjob.step.step1;

// 데이터 품질 오류로 스킵 대상인 예외
public class SkippableFormatJobException extends RuntimeException {
    public SkippableFormatJobException(String message) {
        super(message);
    }

    public SkippableFormatJobException(String message, Throwable cause) {
        super(message, cause);
    }
}

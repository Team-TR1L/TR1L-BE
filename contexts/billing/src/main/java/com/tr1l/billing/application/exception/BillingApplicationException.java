package com.tr1l.billing.application.exception;

import com.tr1l.error.BaseException;
import com.tr1l.error.ErrorCode;

public class BillingApplicationException extends BaseException {
    public BillingApplicationException(ErrorCode code) {
        super(code, null);
    }

    public BillingApplicationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}

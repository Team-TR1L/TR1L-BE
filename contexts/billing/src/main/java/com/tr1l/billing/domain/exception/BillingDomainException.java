package com.tr1l.billing.domain.exception;

import com.tr1l.error.BaseException;
import com.tr1l.error.ErrorCode;

public class BillingDomainException extends BaseException {
    public BillingDomainException(ErrorCode code){super(code,null);}
    public BillingDomainException(ErrorCode code,Throwable cause){super(code,cause);}
}

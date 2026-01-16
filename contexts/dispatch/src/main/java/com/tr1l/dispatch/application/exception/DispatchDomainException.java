package com.tr1l.dispatch.application.exception;

import com.tr1l.error.BaseException;
import com.tr1l.error.ErrorCode;

public class DispatchDomainException extends BaseException {
    public DispatchDomainException(ErrorCode code){super(code,null);}
    public DispatchDomainException(ErrorCode code,Throwable cause){super(code,cause);}
}

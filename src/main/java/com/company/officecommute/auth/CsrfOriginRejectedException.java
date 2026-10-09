package com.company.officecommute.auth;

public class CsrfOriginRejectedException extends RuntimeException {

    public CsrfOriginRejectedException() {
        super("허용되지 않은 출처의 요청입니다.");
    }
}

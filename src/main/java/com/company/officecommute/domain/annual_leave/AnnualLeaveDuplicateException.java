package com.company.officecommute.domain.annual_leave;

public class AnnualLeaveDuplicateException extends RuntimeException {
    public AnnualLeaveDuplicateException() {
        super("이미 등록된 휴가입니다.");
    }
}

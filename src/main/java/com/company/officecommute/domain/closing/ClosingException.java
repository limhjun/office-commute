package com.company.officecommute.domain.closing;

public class ClosingException extends RuntimeException {

    private final ClosingErrorCode code;

    public ClosingException(ClosingErrorCode code) {
        this(code, code.getDefaultMessage());
    }

    public ClosingException(ClosingErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ClosingException(ClosingErrorCode code, Throwable cause) {
        super(code.getDefaultMessage(), cause);
        this.code = code;
    }

    public ClosingErrorCode getCode() {
        return code;
    }
}

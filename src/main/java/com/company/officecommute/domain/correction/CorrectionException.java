package com.company.officecommute.domain.correction;

public class CorrectionException extends RuntimeException {

    private final CorrectionErrorCode code;

    public CorrectionException(CorrectionErrorCode code) {
        this(code, code.getDefaultMessage());
    }

    public CorrectionException(CorrectionErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CorrectionException(CorrectionErrorCode code, Throwable cause) {
        super(code.getDefaultMessage(), cause);
        this.code = code;
    }

    public CorrectionErrorCode getCode() {
        return code;
    }
}

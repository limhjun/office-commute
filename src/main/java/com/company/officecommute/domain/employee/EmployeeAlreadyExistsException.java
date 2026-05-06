package com.company.officecommute.domain.employee;

public class EmployeeAlreadyExistsException extends RuntimeException {
    public EmployeeAlreadyExistsException(String message) {
        super(message);
    }

    public static EmployeeAlreadyExistsException ofEmployeeCode(String employeeCode) {
        return new EmployeeAlreadyExistsException(
                String.format("이미 존재하는 직원 코드입니다: %s", employeeCode));
    }

    public static EmployeeAlreadyExistsException ofEmail(String email) {
        return new EmployeeAlreadyExistsException(
                String.format("이미 존재하는 이메일입니다: %s", email));
    }
}

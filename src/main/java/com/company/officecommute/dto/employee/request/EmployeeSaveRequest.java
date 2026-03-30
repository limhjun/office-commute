package com.company.officecommute.dto.employee.request;

import com.company.officecommute.domain.employee.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record EmployeeSaveRequest(

        @NotBlank(message = "직원 이름은 필수입니다.")
        String name,

        @NotNull(message = "역할은 필수입니다.")
        Role role,

        @NotNull(message = "생일은 필수입니다.")
        @Past(message = "생일은 과거 날짜여야 합니다.")
        LocalDate birthday,

        @NotNull(message = "입사일은 필수입니다.")
        @PastOrPresent(message = "입사일은 오늘이거나 과거 날짜여야 합니다.")
        LocalDate workStartDate,

        @NotBlank(message = "직원 코드(사번)는 필수입니다.")
        @Pattern(regexp = "^[A-Z0-9]{6,10}$",
                message = "사번은 대문자와 숫자 6-10자리여야 합니다.")
        String employeeCode,

        @NotBlank(message = "PIN은 필수입니다.")
        @Pattern(regexp = "^\\d{4,6}$",
                message = "PIN은 4~6자리 숫자여야 합니다.")
        String pin
) {
}

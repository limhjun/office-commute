package com.company.officecommute.domain.employee;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.service.employee.EmployeeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeTest {

    @ParameterizedTest
    @NullAndEmptySource
    void testEmployeeNameException(String input) {
        assertThatThrownBy(() -> new EmployeeBuilder()
                        .withId(1L)
                        .withName(input)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("employee의 name이 올바르지 않은 형식입니다.");
    }

    @Test
    void testChangeTeam() {
        Employee employee = new EmployeeBuilder()
                .withId(1L)
                .withName("hyungjunn")
                .withRole(Role.MANAGER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2021, 8, 18))
                .withEmployeeCode("EMP001")
                .withPin("1234")
                .build();

        employee.changeTeam(new Team("A"));
        assertThat(employee.getTeamName()).isEqualTo("A");
    }
}

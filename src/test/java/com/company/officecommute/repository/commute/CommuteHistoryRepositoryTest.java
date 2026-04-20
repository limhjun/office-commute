package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import com.company.officecommute.service.overtime.TotalWorkingMinutes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CommuteHistoryRepositoryTest {

    @Autowired
    private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private TeamRepository teamRepository;

    @Test
    @DisplayName("월별 근무 시간 조회 시 팀 미배정 직원은 '미배정'으로 반환된다")
    void findWithEmployeeIdByDateRange_includesUnassignedTeamWithDefaultName() {
        // given
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        ZonedDateTime startOfMonth = ZonedDateTime.of(2024, 8, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime endOfMonth = ZonedDateTime.of(2024, 8, 31, 23, 59, 59, 0, zoneId);

        Team backendTeam = teamRepository.save(new Team("백엔드팀"));

        Employee assignedEmployee = new Employee(
                null,
                backendTeam,
                "배정된직원",
                Role.MEMBER,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2020, 1, 1),
                "EMP001",
                "assigned@company.com",
                "password123"
        );

        Employee unassignedEmployee = new Employee(
                null,
                null,
                "미배정직원",
                Role.MEMBER,
                LocalDate.of(1991, 2, 2),
                LocalDate.of(2021, 2, 2),
                "EMP002",
                "unassigned@company.com",
                "password123"
        );

        employeeRepository.saveAll(List.of(assignedEmployee, unassignedEmployee));

        CommuteHistory assignedDay1 = new CommuteHistory(
                null,
                assignedEmployee.getEmployeeId(),
                ZonedDateTime.of(2024, 8, 5, 9, 0, 0, 0, zoneId),
                ZonedDateTime.of(2024, 8, 5, 18, 0, 0, 0, zoneId),
                540
        );
        CommuteHistory assignedDay2 = new CommuteHistory(
                null,
                assignedEmployee.getEmployeeId(),
                ZonedDateTime.of(2024, 8, 6, 9, 0, 0, 0, zoneId),
                ZonedDateTime.of(2024, 8, 6, 17, 0, 0, 0, zoneId),
                480
        );
        CommuteHistory unassignedDay = new CommuteHistory(
                null,
                unassignedEmployee.getEmployeeId(),
                ZonedDateTime.of(2024, 8, 7, 9, 0, 0, 0, zoneId),
                ZonedDateTime.of(2024, 8, 7, 19, 0, 0, 0, zoneId),
                600
        );

        commuteHistoryRepository.saveAll(List.of(assignedDay1, assignedDay2, unassignedDay));

        // when
        List<TotalWorkingMinutes> result = commuteHistoryRepository.findWithEmployeeIdByDateRange(startOfMonth, endOfMonth);

        // then
        assertThat(result).hasSize(2);

        assertThat(result).anySatisfy(total -> {
            if (total.getEmployeeId().equals(assignedEmployee.getEmployeeId())) {
                assertThat(total.getTeamName()).isEqualTo("백엔드팀");
                assertThat(total.calculateOverTime(0)).isEqualTo(1020); // 540 + 480
            }
        });

        assertThat(result).anySatisfy(total -> {
            if (total.getEmployeeId().equals(unassignedEmployee.getEmployeeId())) {
                assertThat(total.getTeamName()).isEqualTo("미배정");
                assertThat(total.calculateOverTime(0)).isEqualTo(600);
            }
        });
    }
}

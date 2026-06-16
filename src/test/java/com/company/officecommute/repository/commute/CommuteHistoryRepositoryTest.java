package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
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
    void findTotalWorkingMinutesByWorkDateBetween_includesUnassignedTeamWithDefaultName() {
        // given
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        LocalDate startDate = LocalDate.of(2024, 8, 1);
        LocalDate endDate = LocalDate.of(2024, 8, 31);

        Team backendTeam = teamRepository.save(new Team("백엔드팀"));

        Employee assignedEmployee = employee("배정된직원", backendTeam, "EMP001", "assigned@company.com");

        Employee unassignedEmployee = employee("미배정직원", null, "EMP002", "unassigned@company.com");

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
        List<TotalWorkingMinutes> result = commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(startDate, endDate);

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

    @Test
    @DisplayName("findAllByEmployeeIdAndWorkDateBetween — 1일과 말일은 포함, 전월 말일과 다음월 1일은 제외")
    void findAllByEmployeeIdAndWorkDateBetween_respectsMonthBoundaries() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Long employeeId = 42L;
        // 7/31(전월 말일), 8/1(당월 1일), 8/31(당월 말일), 9/1(다음월 1일)
        commuteHistoryRepository.saveAll(List.of(
                new CommuteHistory(null, employeeId,
                        ZonedDateTime.of(2024, 7, 31, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 7, 31, 18, 0, 0, 0, zone), 540, zone),
                new CommuteHistory(null, employeeId,
                        ZonedDateTime.of(2024, 8, 1, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 8, 1, 18, 0, 0, 0, zone), 540, zone),
                new CommuteHistory(null, employeeId,
                        ZonedDateTime.of(2024, 8, 31, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 8, 31, 18, 0, 0, 0, zone), 540, zone),
                new CommuteHistory(null, employeeId,
                        ZonedDateTime.of(2024, 9, 1, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 9, 1, 18, 0, 0, 0, zone), 540, zone)
        ));

        // when
        List<CommuteHistory> august = commuteHistoryRepository.findAllByEmployeeIdAndWorkDateBetween(
                employeeId, LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then
        assertThat(august)
                .extracting(CommuteHistory::getWorkDate)
                .containsExactlyInAnyOrder(LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));
    }

    @Test
    @DisplayName("findTotalWorkingMinutesByWorkDateBetween — 연차 레코드는 annualLeaveDate가 속한 월에 집계된다")
    void findTotalWorkingMinutesByWorkDateBetween_aggregatesAnnualLeaveByWorkDate() {
        // given — 연차만(8/1). 연차의 등록 시각(workStartTime=now)은 8월이 아니지만 work_date 기준으로 8월에 잡혀야 한다.
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Team team = teamRepository.save(new Team("백엔드팀"));
        Employee employee = employee("연차직원", team, "EMP100", "leave@company.com");
        employeeRepository.save(employee);
        CommuteHistory annualLeave = new CommuteHistory(employee.getEmployeeId(), LocalDate.of(2024, 8, 1), zone);
        commuteHistoryRepository.save(annualLeave);

        // when
        List<TotalWorkingMinutes> result = commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(
                LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then — 연차가 8월 집계에 포함되어 직원이 결과에 나타난다(근무분은 0)
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEmployeeId()).isEqualTo(employee.getEmployeeId());
        assertThat(result.getFirst().calculateOverTime(0)).isEqualTo(0);
    }

    private Employee employee(String name, Team team, String employeeCode, String email) {
        return new EmployeeBuilder()
                .withTeam(team)
                .withName(name)
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2020, 1, 1))
                .withEmployeeCode(employeeCode)
                .withEmail(email)
                .withPassword("password123")
                .build();
    }

    @Test
    @DisplayName("existsByEmployeeIdAndWorkDate — 동일 일자 기록이 있으면 true")
    void existsByEmployeeIdAndWorkDate_returnsTrue_whenRecordExists() {
        // given
        Long employeeId = 1L;
        ZonedDateTime start = ZonedDateTime.of(2026, 5, 23, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        commuteHistoryRepository.save(new CommuteHistory(null, employeeId, start, null, 0, ZoneId.of("Asia/Seoul")));

        // when
        boolean exists = commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, LocalDate.of(2026, 5, 23));

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("updateWorkEndTimeIfOpen — 미종료 근무면 1건 update되고 퇴근 시각과 근무 분이 반영된다")
    void updateWorkEndTimeIfOpen_updatesOpenCommute() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone);
        CommuteHistory open = commuteHistoryRepository.save(
                new CommuteHistory(null, 1L, start, null, 0, zone));

        // when
        int updated = commuteHistoryRepository.updateWorkEndTimeIfOpen(
                open.getCommuteHistoryId(), start.plusHours(9), 540);

        // then
        assertThat(updated).isEqualTo(1);
        CommuteHistory found = commuteHistoryRepository.findById(open.getCommuteHistoryId()).orElseThrow();
        assertThat(found.getWorkEndTime().toInstant()).isEqualTo(start.plusHours(9).toInstant());
        assertThat(found.getWorkingMinutes()).isEqualTo(540);
    }

    @Test
    @DisplayName("updateWorkEndTimeIfOpen — 이미 종료된 근무면 0건이고 기존 값이 유지된다")
    void updateWorkEndTimeIfOpen_returnsZero_whenAlreadyEnded() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone);
        ZonedDateTime firstEnd = start.plusHours(9);
        CommuteHistory ended = commuteHistoryRepository.save(
                new CommuteHistory(null, 1L, start, firstEnd, 540, zone));

        // when
        int updated = commuteHistoryRepository.updateWorkEndTimeIfOpen(
                ended.getCommuteHistoryId(), start.plusHours(10), 600);

        // then
        assertThat(updated).isZero();
        CommuteHistory found = commuteHistoryRepository.findById(ended.getCommuteHistoryId()).orElseThrow();
        assertThat(found.getWorkEndTime().toInstant()).isEqualTo(firstEnd.toInstant());
        assertThat(found.getWorkingMinutes()).isEqualTo(540);
    }

    @Test
    @DisplayName("existsByEmployeeIdAndWorkDate — 다른 일자만 있으면 false")
    void existsByEmployeeIdAndWorkDate_returnsFalse_whenNoRecordOnThatDate() {
        // given
        Long employeeId = 1L;
        ZonedDateTime start = ZonedDateTime.of(2026, 5, 22, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        commuteHistoryRepository.save(new CommuteHistory(null, employeeId, start, null, 0, ZoneId.of("Asia/Seoul")));

        // when
        boolean exists = commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, LocalDate.of(2026, 5, 23));

        // then
        assertThat(exists).isFalse();
    }
}

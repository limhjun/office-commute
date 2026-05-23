package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.commute.PreviousCommuteNotEndedException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class CommuteHistoryServiceConcurrencyTest {

    @Autowired private CommuteHistoryService commuteHistoryService;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TeamRepository teamRepository;

    private Long testEmployeeId;

    @BeforeEach
    void setup() {
        commuteHistoryRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();

        Team team = new Team("테스트팀");
        teamRepository.save(team);

        Employee employee = new Employee(
                null,
                team,
                "테스트직원",
                Role.MEMBER,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2024, 1, 1),
                "TEST001",
                "test@company.com",
                "password123"
        );
        Employee savedEmployee = employeeRepository.save(employee);
        testEmployeeId = savedEmployee.getEmployeeId();
    }

    @AfterEach
    void cleanup() {
        commuteHistoryRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @Test
    @DisplayName("동시 출근 등록 테스트 - H2 DB")
    void testConcurrentRegisterWorkStartTime_H2DB() throws InterruptedException {
        Long employeeId = testEmployeeId;
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    commuteHistoryService.registerWorkStartTime(employeeId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failures.add(e);
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        List<CommuteHistory> histories = commuteHistoryRepository.findAll();
        assertThat(histories).hasSize(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
        assertThat(failures)
                .hasSize(threadCount - 1)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(DuplicateWorkOnDateException.class));
    }

    @Test
    @DisplayName("같은 날 미완료 근무 중 재출근시 DuplicateWorkOnDateException (광클 방어)")
    void sameDayDoubleStartWhilePreviousOpenThrowsDuplicateWorkOnDate() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");
    }

    @Test
    @DisplayName("어제 미완료 근무 + 오늘 첫 출근시 PreviousCommuteNotEndedException")
    void crossDayPreviousOpenStillTriggersPreviousCommuteNotEnded() {
        ZonedDateTime yesterdayStart = ZonedDateTime.now()
                .minusDays(1)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        commuteHistoryRepository.save(new CommuteHistory(
                null,
                testEmployeeId,
                yesterdayStart,
                null,
                0,
                yesterdayStart.getZone()
        ));

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(PreviousCommuteNotEndedException.class)
                .hasMessage("이전 근무가 아직 종료되지 않았습니다.");
    }

    @Test
    @DisplayName("미래 연차 기록이 있어도 오늘 실제 출근과 퇴근이 가능하다")
    void testFutureAnnualLeaveDoesNotBlockTodayWorkStartAndEnd() {
        ZonedDateTime futureAnnualLeaveStartTime = LocalDate.now()
                .plusDays(10)
                .atStartOfDay(ZonedDateTime.now().getZone());
        commuteHistoryRepository.save(new CommuteHistory(
                null,
                testEmployeeId,
                futureAnnualLeaveStartTime,
                null,
                0,
                true
        ));

        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThat(commuteHistoryRepository.findAll()).hasSize(2);
        assertThat(commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(testEmployeeId))
                .isEmpty();
    }

    @Test
    @DisplayName("퇴근 후 같은 날 재출근시 DuplicateWorkOnDateException")
    void sameDaySecondStartThrowsDuplicateWorkOnDate() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");
    }
}

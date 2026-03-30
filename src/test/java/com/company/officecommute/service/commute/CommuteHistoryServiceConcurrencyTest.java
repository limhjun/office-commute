package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
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
                null,
                Role.MEMBER,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2024, 1, 1),
                "TEST001",
                "1234"
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

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    commuteHistoryService.registerWorkStartTime(employeeId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
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
    }

    @Test
    @DisplayName("이전 출근 미완료시 비즈니스 검증 실패")
    void testBusinessValidationFailure() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이전 근무가 아직 종료되지 않았습니다.");
    }

    @Test
    @DisplayName("퇴근 후 같은 날 재출근시 DB 제약조건 위반")
    void testDatabaseConstraintViolation() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId, ZonedDateTime.now());

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

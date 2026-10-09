package com.company.officecommute.service.correction;

import com.company.officecommute.config.MutableClock;
import com.company.officecommute.config.MutableClockConfig;
import com.company.officecommute.domain.closing.ClosingErrorCode;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.closing.request.MonthlyClosingCreateRequest;
import com.company.officecommute.dto.correction.request.CorrectionCreateRequest;
import com.company.officecommute.dto.correction.response.CorrectionRequestResponse;
import com.company.officecommute.repository.closing.MonthlyClosingRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.closing.MonthlyClosingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 기록·같은 요청·마감에 대한 동시 쓰기가 직원 행 잠금과 DB 제약으로 정리되는지 검증한다(H2).
 * MySQL 의 잠금 동작은 Testcontainers 테스트에서 따로 확인해야 한다.
 */
@SpringBootTest
@Import(MutableClockConfig.class)
class CommuteCorrectionConcurrentTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private CommuteCorrectionService correctionService;
    @Autowired private MonthlyClosingService monthlyClosingService;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private CommuteCorrectionRequestRepository correctionRequestRepository;
    @Autowired private MonthlyClosingRepository monthlyClosingRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private MutableClock clock;

    private Long memberId;
    private Long managerId;

    @BeforeEach
    void setUp() {
        cleanUp();
        clock.setInstant(MutableClockConfig.DEFAULT_NOW);
        memberId = saveEmployee(Role.MEMBER, "CMEM001");
        managerId = saveEmployee(Role.MANAGER, "CMGR001");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        correctionRequestRepository.deleteAllInBatch();
        monthlyClosingRepository.deleteAllInBatch();
        commuteHistoryRepository.deleteAllInBatch();
        employeeRepository.saveAll(employeeRepository.findAll().stream()
                .peek(employee -> employee.assignCorrectionApprover(null))
                .toList());
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 기록에 동시에 신청하면 대기 요청은 정확히 하나만 생긴다")
    void concurrentSubmit_exactlyOnePending() throws InterruptedException {
        CommuteHistory open = saveOpen(at(2026, 9, 2, 9, 0));

        Result result = runConcurrently(10, index -> correctionService.submit(memberId, new CorrectionCreateRequest(
                open.getCommuteHistoryId(), open.getVersion(),
                at(2026, 9, 2, 18, index).toOffsetDateTime(), "동시 신청")));

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.failures()).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(CorrectionException.class)
                .extracting(e -> ((CorrectionException) e).getCode())
                .isEqualTo(CorrectionErrorCode.CORRECTION_ALREADY_PENDING));
        assertThat(correctionRequestRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("승인·반려·취소가 겹치면 하나의 상태 전이만 성공한다")
    void concurrentTransitions_exactlyOneWins() throws InterruptedException {
        CommuteHistory open = saveOpen(at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse request = correctionService.submit(memberId, new CorrectionCreateRequest(
                open.getCommuteHistoryId(), open.getVersion(), at(2026, 9, 2, 18, 0).toOffsetDateTime(), "사유"));

        Result result = runConcurrently(9, index -> switch (index % 3) {
            case 0 -> correctionService.approve(managerId, request.requestId(), null);
            case 1 -> correctionService.reject(managerId, request.requestId(), "반려");
            default -> correctionService.cancel(memberId, request.requestId());
        });

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.failures()).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(CorrectionException.class)
                .extracting(e -> ((CorrectionException) e).getCode())
                .isEqualTo(CorrectionErrorCode.CORRECTION_ALREADY_PROCESSED));
        CorrectionStatus finalStatus = correctionRequestRepository.findById(request.requestId()).orElseThrow().getStatus();
        CommuteHistory after = commuteHistoryRepository.findById(open.getCommuteHistoryId()).orElseThrow();
        // 승인이 이긴 경우에만 원본이 바뀐다
        assertThat(after.endTimeIsNull()).isEqualTo(finalStatus != CorrectionStatus.APPROVED);
    }

    @Test
    @DisplayName("마감과 신청이 겹쳐도 '마감됐는데 입력 기간에 대기 요청이 있는' 상태는 생기지 않는다")
    void closingAndSubmitRace_neverLeavesPendingInClosedRange() throws InterruptedException {
        List<CommuteHistory> records = new ArrayList<>();
        for (int day = 1; day <= 6; day++) {
            records.add(commuteHistoryRepository.save(CommuteHistoryFixture.ended(
                    null, memberId, at(2026, 9, day, 9, 0), at(2026, 9, day, 18, 0), KST)));
        }

        Result result = runConcurrently(records.size() + 1, index -> {
            if (index == records.size()) {
                return monthlyClosingService.close(managerId,
                        new MonthlyClosingCreateRequest(YearMonth.of(2026, 9), null, null));
            }
            CommuteHistory target = records.get(index);
            return correctionService.submit(memberId, new CorrectionCreateRequest(
                    target.getCommuteHistoryId(), target.getVersion(),
                    at(2026, 9, index + 1, 19, 0).toOffsetDateTime(), "마감 경쟁"));
        });

        boolean closed = monthlyClosingRepository.existsByTargetYearMonth(YearMonth.of(2026, 9));
        long pendingInRange = correctionRequestRepository.countByStatusAndWorkDateBetween(
                CorrectionStatus.PENDING, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30));
        assertThat(closed && pendingInRange > 0).isFalse();
        assertThat(result.failures()).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(ClosingException.class)
                .extracting(e -> ((ClosingException) e).getCode())
                .isIn(ClosingErrorCode.CLOSING_PERIOD_LOCKED, ClosingErrorCode.CLOSING_HAS_UNRESOLVED));
    }

    private interface IndexedTask {
        Object run(int index) throws Exception;
    }

    private record Result(int successes, List<Throwable> failures) {
    }

    private Result runConcurrently(int threadCount, IndexedTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run(index);
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        return new Result(successes.get(), List.copyOf(failures));
    }

    private Long saveEmployee(Role role, String code) {
        Employee employee = new EmployeeBuilder()
                .withName(code)
                .withRole(role)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode(code)
                .withEmail(code.toLowerCase() + "@company.com")
                .withPassword("password123")
                .build();
        return employeeRepository.save(employee).getEmployeeId();
    }

    private CommuteHistory saveOpen(ZonedDateTime start) {
        return commuteHistoryRepository.save(CommuteHistoryFixture.open(null, memberId, start, KST));
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, KST);
    }
}

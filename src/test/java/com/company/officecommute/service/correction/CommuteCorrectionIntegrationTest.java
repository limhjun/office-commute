package com.company.officecommute.service.correction;

import com.company.officecommute.auth.ForbiddenException;
import com.company.officecommute.config.MutableClock;
import com.company.officecommute.config.MutableClockConfig;
import com.company.officecommute.domain.closing.ClosingErrorCode;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.closing.LegacyResolution;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.commute.CommuteStatus;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.dto.closing.request.MonthlyClosingCreateRequest;
import com.company.officecommute.dto.closing.response.MonthlyClosingResponse;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse.ClosingBlocker;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.correction.request.CorrectionCreateRequest;
import com.company.officecommute.dto.correction.response.CorrectionRequestResponse;
import com.company.officecommute.repository.closing.MonthlyClosingRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.repository.report.ReportFileRepository;
import com.company.officecommute.service.closing.MonthlyClosingService;
import com.company.officecommute.service.commute.CommuteHistoryService;
import com.company.officecommute.service.employee.EmployeeService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willReturn;

/**
 * 정정 신청·승인·반려·취소와 월 마감이 같은 근태 행·잠금 규칙을 공유하는지 실제 트랜잭션으로 검증한다.
 * 시각은 {@link MutableClock}으로 고정한다(기본 2026-10-09 12:00 KST).
 */
@SpringBootTest
@Import(MutableClockConfig.class)
class CommuteCorrectionIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    @Autowired private CommuteCorrectionService correctionService;
    @Autowired private CommuteHistoryService commuteHistoryService;
    @Autowired private MonthlyClosingService monthlyClosingService;
    @Autowired private EmployeeService employeeService;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private CommuteCorrectionRequestRepository correctionRequestRepository;
    @Autowired private MonthlyClosingRepository monthlyClosingRepository;
    @Autowired private ReportDispatchRepository reportDispatchRepository;
    @Autowired private ReportFileRepository reportFileRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private MutableClock clock;

    @MockitoSpyBean
    private CommuteHistoryRepository spiedCommuteHistoryRepository;

    private Long memberId;
    private Long managerId;
    private Long otherManagerId;
    private Long approverId;

    @BeforeEach
    void setUp() {
        cleanUp();
        clock.setInstant(MutableClockConfig.DEFAULT_NOW);
        memberId = saveEmployee("일반직원", Role.MEMBER, "MEM0001");
        managerId = saveEmployee("관리자A", Role.MANAGER, "MGR0001");
        otherManagerId = saveEmployee("관리자B", Role.MANAGER, "MGR0002");
        approverId = saveEmployee("상위승인자", Role.COMMUTE_APPROVER, "APR0001");
        employeeService.assignCorrectionApprover(managerId, approverId);
        employeeService.assignCorrectionApprover(approverId, managerId);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        correctionRequestRepository.deleteAllInBatch();
        monthlyClosingRepository.deleteAllInBatch();
        reportFileRepository.deleteAllInBatch();
        reportDispatchRepository.deleteAllInBatch();
        commuteHistoryRepository.deleteAllInBatch();
        employeeRepository.saveAll(employeeRepository.findAll().stream()
                .peek(employee -> employee.assignCorrectionApprover(null))
                .toList());
        employeeRepository.deleteAll();
    }

    // ---- 신청·승인·반려·취소 ----

    @Test
    @DisplayName("신청은 원본을 바꾸지 않고, 승인만 같은 행의 종료 시각·근무 분·버전을 바꾼다")
    void approveUpdatesOnlyOnApproval() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));

        CorrectionRequestResponse submitted = submit(memberId, open, at(2026, 9, 2, 19, 30));
        CommuteHistory afterSubmit = reload(open);
        assertThat(afterSubmit.endTimeIsNull()).isTrue();
        assertThat(afterSubmit.getVersion()).isEqualTo(open.getVersion());
        assertThat(submitted.status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(submitted.requestedWorkingMinutes()).isEqualTo(10L * 60 + 30);
        assertThat(submitted.previousWorkEndTime()).isNull();

        CorrectionRequestResponse approved = correctionService.approve(managerId, submitted.requestId(), "확인함");

        CommuteHistory afterApprove = reload(open);
        assertThat(afterApprove.getWorkEndTime()).isEqualTo(at(2026, 9, 2, 19, 30).toInstant());
        assertThat(afterApprove.getWorkingMinutes()).isEqualTo(10L * 60 + 30);
        assertThat(afterApprove.getVersion()).isEqualTo(open.getVersion() + 1);
        // 근무일·시간대·출근 시각은 그대로다
        assertThat(afterApprove.getWorkDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(afterApprove.getWorkZone()).isEqualTo("Asia/Seoul");
        assertThat(afterApprove.getWorkStartTime()).isEqualTo(open.getWorkStartTime());

        assertThat(approved.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(approved.processedBy().employeeId()).isEqualTo(managerId);
        assertThat(approved.reviewComment()).isEqualTo("확인함");
        assertThat(approved.requestedWorkEndTime()).isEqualTo(OffsetDateTime.parse("2026-09-02T19:30:00+09:00"));
    }

    @Test
    @DisplayName("반려·취소는 원본을 바꾸지 않고 이력으로 남으며, 이후 같은 기록에 다시 신청할 수 있다")
    void rejectAndCancelKeepOriginalAndAllowResubmit() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));

        CorrectionRequestResponse first = submit(memberId, open, at(2026, 9, 2, 18, 0));
        CorrectionRequestResponse rejected = correctionService.reject(managerId, first.requestId(), "근거 부족");
        assertThat(rejected.status()).isEqualTo(CorrectionStatus.REJECTED);
        assertThat(rejected.reviewComment()).isEqualTo("근거 부족");

        CorrectionRequestResponse second = submit(memberId, open, at(2026, 9, 2, 18, 30));
        CorrectionRequestResponse cancelled = correctionService.cancel(memberId, second.requestId());
        assertThat(cancelled.status()).isEqualTo(CorrectionStatus.CANCELLED);
        assertThat(cancelled.processedBy().employeeId()).isEqualTo(memberId);

        assertThat(reload(open).endTimeIsNull()).isTrue();
        CorrectionRequestResponse third = submit(memberId, open, at(2026, 9, 2, 19, 0));
        assertThat(third.status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(correctionService.findMine(memberId, null))
                .extracting(CorrectionRequestResponse::status)
                .containsExactly(CorrectionStatus.PENDING, CorrectionStatus.CANCELLED, CorrectionStatus.REJECTED);
    }

    @Test
    @DisplayName("완료 기록을 반복 정정하면 매번 새 요청으로 남고 승인 당시 전후 값이 보존된다")
    void repeatedCorrectionsPreserveBeforeAndAfter() {
        CommuteHistory ended = commuteHistoryRepository.save(CommuteHistoryFixture.ended(
                null, memberId, at(2026, 9, 3, 9, 0), at(2026, 9, 3, 18, 0), KST));

        CorrectionRequestResponse earlier = submit(memberId, ended, at(2026, 9, 3, 17, 0));
        correctionService.approve(managerId, earlier.requestId(), null);
        CommuteHistory afterFirst = reload(ended);
        CorrectionRequestResponse later = submit(memberId, afterFirst, at(2026, 9, 3, 20, 0));
        correctionService.approve(managerId, later.requestId(), null);

        List<CorrectionRequestResponse> history = correctionService.findMine(memberId, CorrectionStatus.APPROVED);
        assertThat(history).extracting(CorrectionRequestResponse::previousWorkingMinutes,
                        CorrectionRequestResponse::requestedWorkingMinutes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(8L * 60, 11L * 60),
                        org.assertj.core.groups.Tuple.tuple(9L * 60, 8L * 60));
        assertThat(reload(ended).getWorkingMinutes()).isEqualTo(11L * 60);
    }

    @Test
    @DisplayName("승인 정정은 24시간을 넘는 실제 근무도 기록한다")
    void approvalAllowsOver24Hours() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));

        CorrectionRequestResponse request = submit(memberId, open, at(2026, 9, 3, 11, 0));
        correctionService.approve(managerId, request.requestId(), null);

        assertThat(reload(open).getWorkingMinutes()).isEqualTo(26L * 60);
    }

    @Test
    @DisplayName("한 기록에는 대기 요청이 하나뿐이다")
    void onlyOnePendingPerRecord() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));
        submit(memberId, open, at(2026, 9, 2, 18, 0));

        assertCorrection(() -> submit(memberId, open, at(2026, 9, 2, 19, 0)),
                CorrectionErrorCode.CORRECTION_ALREADY_PENDING);
    }

    @Test
    @DisplayName("처리된 요청은 다시 처리할 수 없다")
    void processedRequestCannotTransitionAgain() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse request = submit(memberId, open, at(2026, 9, 2, 18, 0));
        correctionService.reject(managerId, request.requestId(), "반려");

        assertCorrection(() -> correctionService.approve(managerId, request.requestId(), null),
                CorrectionErrorCode.CORRECTION_ALREADY_PROCESSED);
        assertCorrection(() -> correctionService.cancel(memberId, request.requestId()),
                CorrectionErrorCode.CORRECTION_ALREADY_PROCESSED);
    }

    // ---- 권한 ----

    @Test
    @DisplayName("타인 기록 신청·타인 요청 취소·조회 범위 밖 조회를 거부한다")
    void rejectsAccessToOthersRecords() {
        CommuteHistory managersRecord = saveOpen(managerId, at(2026, 9, 2, 9, 0));
        assertThatThrownBy(() -> submit(memberId, managersRecord, at(2026, 9, 2, 18, 0)))
                .isInstanceOf(ForbiddenException.class);

        CommuteHistory open = saveOpen(memberId, at(2026, 9, 3, 9, 0));
        CorrectionRequestResponse request = submit(memberId, open, at(2026, 9, 3, 18, 0));
        assertThatThrownBy(() -> correctionService.cancel(managerId, request.requestId()))
                .isInstanceOf(ForbiddenException.class);
        // 상위 승인자는 지정되지 않은 MEMBER 요청을 볼 수 없다
        assertThatThrownBy(() -> correctionService.findOne(approverId, request.requestId()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(correctionService.findReviewScope(approverId, null)).isEmpty();
        assertThatThrownBy(() -> correctionService.findReviewScope(memberId, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("MEMBER 요청은 MANAGER 가, MANAGER 요청은 지정된 상위 승인자만 처리한다 — 자기 승인 금지")
    void reviewRouting() {
        CommuteHistory membersRecord = saveOpen(memberId, at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse memberRequest = submit(memberId, membersRecord, at(2026, 9, 2, 18, 0));
        assertThatThrownBy(() -> correctionService.approve(approverId, memberRequest.requestId(), null))
                .isInstanceOf(ForbiddenException.class);
        assertThat(correctionService.findReviewScope(otherManagerId, CorrectionStatus.PENDING))
                .extracting(CorrectionRequestResponse::requestId)
                .containsExactly(memberRequest.requestId());

        CommuteHistory managersRecord = saveOpen(managerId, at(2026, 9, 3, 9, 0));
        CorrectionRequestResponse managerRequest = submit(managerId, managersRecord, at(2026, 9, 3, 18, 0));
        assertThat(managerRequest.assignedApprover().employeeId()).isEqualTo(approverId);
        assertCorrection(() -> correctionService.approve(managerId, managerRequest.requestId(), null),
                CorrectionErrorCode.CORRECTION_SELF_APPROVAL);
        assertThatThrownBy(() -> correctionService.approve(otherManagerId, managerRequest.requestId(), null))
                .isInstanceOf(ForbiddenException.class);
        assertThat(correctionService.findReviewScope(otherManagerId, null))
                .extracting(CorrectionRequestResponse::requestId)
                .doesNotContain(managerRequest.requestId());

        CorrectionRequestResponse approved = correctionService.approve(approverId, managerRequest.requestId(), null);
        assertThat(approved.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(correctionService.findReviewScope(approverId, null))
                .extracting(CorrectionRequestResponse::requestId)
                .containsExactly(managerRequest.requestId());
    }

    @Test
    @DisplayName("지정 승인자가 없는 MANAGER 도 신청할 수 있지만, 그 요청은 누구도 승인·반려할 수 없다(계획 1.3)")
    void managerWithoutApproverCanSubmitButNobodyCanReview() {
        CommuteHistory record = saveOpen(otherManagerId, at(2026, 9, 2, 9, 0));

        CorrectionRequestResponse request = submit(otherManagerId, record, at(2026, 9, 2, 18, 0));

        assertThat(request.status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(request.assignedApprover()).isNull();
        assertThat(request.actions().canCancel()).isTrue();
        assertThat(request.actions().canReview()).isFalse();
        // 다른 관리자도, 상위 승인자도 처리할 수 없다 — 원본은 그대로다
        assertCorrection(() -> correctionService.approve(managerId, request.requestId(), null),
                CorrectionErrorCode.CORRECTION_APPROVER_NOT_ASSIGNED);
        assertCorrection(() -> correctionService.reject(approverId, request.requestId(), "반려"),
                CorrectionErrorCode.CORRECTION_APPROVER_NOT_ASSIGNED);
        assertThat(reload(record).endTimeIsNull()).isTrue();
        assertThat(correctionService.findReviewScope(managerId, CorrectionStatus.PENDING))
                .extracting(CorrectionRequestResponse::requestId)
                .doesNotContain(request.requestId());
        // 승인 대기 요청이므로 월 마감도 막는다
        assertThat(monthlyClosingService.getStatus(SEPTEMBER).blockers())
                .contains(ClosingBlocker.PENDING_CORRECTIONS);
    }

    @Test
    @DisplayName("담당자 없이 신청한 요청은 취소 → 담당자 지정 → 재신청 순서로 처리한다")
    void unassignedRequestIsResolvedByCancelAssignResubmit() {
        CommuteHistory record = saveOpen(otherManagerId, at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse unassigned = submit(otherManagerId, record, at(2026, 9, 2, 18, 0));

        // 대기 중에는 미지정 → 지정도 "변경"이므로 막힌다
        assertCorrection(() -> employeeService.assignCorrectionApprover(otherManagerId, approverId),
                CorrectionErrorCode.PENDING_CORRECTION_EXISTS);

        correctionService.cancel(otherManagerId, unassigned.requestId());
        employeeService.assignCorrectionApprover(otherManagerId, approverId);
        CorrectionRequestResponse resubmitted = submit(otherManagerId, record, at(2026, 9, 2, 18, 0));

        assertThat(resubmitted.assignedApprover().employeeId()).isEqualTo(approverId);
        assertThat(correctionService.approve(approverId, resubmitted.requestId(), null).status())
                .isEqualTo(CorrectionStatus.APPROVED);
    }

    @Test
    @DisplayName("대기 요청이 있으면 담당자 변경을 막고, 취소 후에는 허용한다")
    void approverChangeBlockedWhilePending() {
        CommuteHistory record = saveOpen(managerId, at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse request = submit(managerId, record, at(2026, 9, 2, 18, 0));

        assertCorrection(() -> employeeService.assignCorrectionApprover(managerId, null),
                CorrectionErrorCode.PENDING_CORRECTION_EXISTS);
        assertCorrection(() -> employeeService.changeWorkEndDate(approverId, LocalDate.of(2026, 10, 31)),
                CorrectionErrorCode.PENDING_CORRECTION_EXISTS);

        correctionService.cancel(managerId, request.requestId());
        employeeService.assignCorrectionApprover(managerId, null);
        assertThat(employeeRepository.findById(managerId).orElseThrow().getCorrectionApproverId()).isNull();
    }

    // ---- 시각 검증과 원본 충돌 ----

    @Test
    @DisplayName("빈 사유가 아니라도 출근 이전·미래·연차는 거부한다")
    void rejectsInvalidTimes() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));
        assertCorrection(() -> submit(memberId, open, at(2026, 9, 2, 8, 59)),
                CorrectionErrorCode.CORRECTION_END_BEFORE_START);
        assertCorrection(() -> submit(memberId, open, ZonedDateTime.ofInstant(clock.instant().plusSeconds(60), KST)),
                CorrectionErrorCode.CORRECTION_END_IN_FUTURE);

        CommuteHistory dayOff = commuteHistoryRepository.save(
                CommuteHistoryFixture.annualLeave(memberId, LocalDate.of(2026, 9, 4), KST));
        assertCorrection(() -> submit(memberId, dayOff, at(2026, 9, 4, 18, 0)),
                CorrectionErrorCode.CORRECTION_TARGET_DAY_OFF);
    }

    @Test
    @DisplayName("출근 시각에 소수 초가 있어도 같은 시각 정정은 0분으로 받고, 그보다 이르면 거부한다")
    void endEqualToFractionalStartIsAllowed() {
        ZonedDateTime start = at(2026, 9, 2, 9, 0).plusNanos(123_456_000);
        CommuteHistory open = saveOpen(memberId, start);
        assertCorrection(() -> submit(memberId, open, start.minusNanos(1_000)),
                CorrectionErrorCode.CORRECTION_END_BEFORE_START);

        CorrectionRequestResponse submitted = submit(memberId, open, start);
        assertThat(submitted.requestedWorkingMinutes()).isZero();

        correctionService.approve(managerId, submitted.requestId(), null);
        CommuteHistory approved = reload(open);
        assertThat(approved.getWorkEndTime()).isEqualTo(start.toInstant());
        assertThat(approved.getWorkingMinutes()).isZero();
    }

    @Test
    @DisplayName("신청 시각의 마이크로초 미만은 버리고 저장한다")
    void requestedEndTruncatedToMicros() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));

        CorrectionRequestResponse submitted = submit(memberId, open, at(2026, 9, 2, 18, 0).plusNanos(123_456_789));
        correctionService.approve(managerId, submitted.requestId(), null);

        assertThat(reload(open).getWorkEndTime()).isEqualTo(at(2026, 9, 2, 18, 0).plusNanos(123_456_000).toInstant());
    }

    @Test
    @DisplayName("신청 후 같은 기록이 바뀌면 승인하지 않고 요청은 PENDING 으로 남는다")
    void sameRecordChangedAfterSubmitIsConflict() {
        // 오늘(10/9) 08:00 출근 — 최신 근무, 24시간 이내
        clock.setInstant(at(2026, 10, 9, 8, 0).toInstant());
        commuteHistoryService.registerWorkStartTime(memberId);
        CommuteHistory today = latestOf(memberId);
        clock.setInstant(at(2026, 10, 9, 12, 0).toInstant());
        CorrectionRequestResponse request = submit(memberId, today, at(2026, 10, 9, 11, 0));

        commuteHistoryService.registerWorkEndTime(memberId);

        assertCorrection(() -> correctionService.approve(managerId, request.requestId(), null),
                CorrectionErrorCode.CORRECTION_VERSION_CONFLICT);
        assertThat(correctionRequestRepository.findById(request.requestId()).orElseThrow().getStatus())
                .isEqualTo(CorrectionStatus.PENDING);
    }

    @Test
    @DisplayName("다른 기록의 일반 퇴근은 대상 기록의 충돌이 아니다 — 9/2 정정 대기 중 9/6 퇴근")
    void otherRecordEndIsNotConflict() {
        CommuteHistory sep2 = saveOpen(memberId, at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse request = submit(memberId, sep2, at(2026, 9, 2, 18, 0));

        clock.setInstant(at(2026, 9, 6, 9, 0).toInstant());
        commuteHistoryService.registerWorkStartTime(memberId);
        clock.setInstant(at(2026, 9, 6, 18, 0).toInstant());
        commuteHistoryService.registerWorkEndTime(memberId);
        clock.setInstant(MutableClockConfig.DEFAULT_NOW);

        assertThat(correctionService.approve(managerId, request.requestId(), null).status())
                .isEqualTo(CorrectionStatus.APPROVED);
    }

    @Test
    @DisplayName("신청 후 생긴 후속 출근과 겹치면 승인 시 다시 검사해 거부한다")
    void laterClockInRecheckedOnApproval() {
        CommuteHistory sep2 = saveOpen(memberId, at(2026, 9, 2, 22, 0));
        CorrectionRequestResponse request = submit(memberId, sep2, at(2026, 9, 3, 10, 0));
        commuteHistoryRepository.save(CommuteHistoryFixture.open(null, memberId, at(2026, 9, 3, 9, 0), KST));

        assertCorrection(() -> correctionService.approve(managerId, request.requestId(), null),
                CorrectionErrorCode.CORRECTION_OVERLAPS_NEXT_WORK);
    }

    @Test
    @DisplayName("원본 갱신이 실패하면 요청 상태 전이까지 함께 롤백된다")
    void approvalRollsBackWhenOriginalUpdateFails() {
        CommuteHistory open = saveOpen(memberId, at(2026, 9, 2, 9, 0));
        CorrectionRequestResponse request = submit(memberId, open, at(2026, 9, 2, 18, 0));
        willReturn(0).given(spiedCommuteHistoryRepository)
                .updateWorkEndTimeIfVersion(anyLong(), anyLong(), any(Instant.class), anyLong());

        assertCorrection(() -> correctionService.approve(managerId, request.requestId(), null),
                CorrectionErrorCode.CORRECTION_VERSION_CONFLICT);

        assertThat(correctionRequestRepository.findById(request.requestId()).orElseThrow().getStatus())
                .isEqualTo(CorrectionStatus.PENDING);
        assertThat(reload(open).endTimeIsNull()).isTrue();
    }

    // ---- 상태 표시 ----

    @Test
    @DisplayName("조회 월 밖의 후속 근무도 이전 미퇴근을 CORRECTION_REQUIRED 로 만들고, 대기 요청 ID 를 함께 보여 준다")
    void monthlyViewShowsCorrectionRequiredAndPendingRequest() {
        clock.setInstant(at(2026, 10, 1, 8, 0).toInstant());
        CommuteHistory sep30 = saveOpen(memberId, at(2026, 9, 30, 22, 0));
        commuteHistoryRepository.save(CommuteHistoryFixture.open(null, memberId, at(2026, 10, 1, 7, 0), KST));
        CorrectionRequestResponse request = submit(memberId, sep30, at(2026, 10, 1, 6, 0));

        CommuteDetailResponse detail = commuteHistoryService.getWorkDurationPerDate(memberId, SEPTEMBER)
                .details().getFirst();

        assertThat(detail.status()).isEqualTo(CommuteStatus.CORRECTION_REQUIRED);
        assertThat(detail.pendingCorrectionRequestId()).isEqualTo(request.requestId());
        assertThat(detail.commuteHistoryId()).isEqualTo(sep30.getCommuteHistoryId());
        assertThat(detail.lockReason()).isNull();
    }

    // ---- 월 마감 ----

    @Test
    @DisplayName("미퇴근·대기 요청이 있으면 마감을 막고, 해결 후 마감하면 입력 기간 전체의 쓰기를 막는다")
    void closingBlocksUntilResolvedThenLocks() {
        CommuteHistory aug31 = saveOpen(memberId, at(2026, 8, 31, 9, 0));
        MonthlyClosingStatusResponse status = monthlyClosingService.getStatus(SEPTEMBER);
        assertThat(status.rangeStart()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(status.blockers()).containsExactly(ClosingBlocker.UNCLOSED_COMMUTES);
        assertClosing(() -> close(SEPTEMBER, null), ClosingErrorCode.CLOSING_HAS_UNRESOLVED);

        CorrectionRequestResponse request = submit(memberId, aug31, at(2026, 8, 31, 18, 0));
        assertThat(monthlyClosingService.getStatus(SEPTEMBER).blockers())
                .containsExactly(ClosingBlocker.UNCLOSED_COMMUTES, ClosingBlocker.PENDING_CORRECTIONS);
        correctionService.approve(managerId, request.requestId(), null);

        MonthlyClosingResponse closing = close(SEPTEMBER, null);
        assertThat(closing.type()).isEqualTo(MonthlyClosingType.MANUAL);
        assertThat(closing.closedBy().employeeId()).isEqualTo(managerId);

        // 8월 31일은 8월이 미마감이어도 9월 보고서의 입력이라 정정할 수 없다
        CommuteHistory approvedAug31 = reload(aug31);
        assertClosing(() -> submit(memberId, approvedAug31, at(2026, 8, 31, 19, 0)),
                ClosingErrorCode.CLOSING_PERIOD_LOCKED);
        // 같은 범위의 다른 기록 정정도 막힌다
        CommuteHistory sep10 = commuteHistoryRepository.save(CommuteHistoryFixture.ended(
                null, memberId, at(2026, 9, 10, 9, 0), at(2026, 9, 10, 18, 0), KST));
        assertClosing(() -> submit(memberId, sep10, at(2026, 9, 10, 19, 0)),
                ClosingErrorCode.CLOSING_PERIOD_LOCKED);
        assertThat(commuteHistoryService.getWorkDurationPerDate(memberId, SEPTEMBER).details())
                .allSatisfy(detail -> assertThat(detail.lockReason()).isNotNull());
        // 8월 30일은 9월 보고서 입력이 아니다 — 기간 경과만으로 거부하지 않는다
        CommuteHistory aug30 = saveOpen(memberId, at(2026, 8, 30, 9, 0));
        assertThat(submit(memberId, aug30, at(2026, 8, 30, 18, 0)).status()).isEqualTo(CorrectionStatus.PENDING);
    }

    @Test
    @DisplayName("승인 대기 중 마감된 기록은 승인할 수 없다 — 마감 보호 범위는 승인 시점에도 검사한다")
    void approvalRechecksClosingProtection() {
        CommuteHistory ended = commuteHistoryRepository.save(CommuteHistoryFixture.ended(
                null, memberId, at(2026, 8, 3, 9, 0), at(2026, 8, 3, 18, 0), KST));
        CorrectionRequestResponse request = submit(memberId, ended, at(2026, 8, 3, 19, 0));
        // 8월 마감 기록을 직접 넣는다(대기 요청이 있으면 서비스는 마감을 거부하므로)
        monthlyClosingRepository.save(new com.company.officecommute.domain.closing.MonthlyClosing(
                AUGUST, MonthlyClosingType.MANUAL, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 31),
                managerId, clock.instant(), null));

        assertClosing(() -> correctionService.approve(managerId, request.requestId(), null),
                ClosingErrorCode.CLOSING_PERIOD_LOCKED);
    }

    @Test
    @DisplayName("한국 시간 기준 진행 중인 월과 미래 월은 마감할 수 없고, 끝난 월은 마감할 수 있다")
    void onlyEndedMonthsCanBeClosed() {
        // 2026-09-30 23:30 KST — 9월은 아직 진행 중
        clock.setInstant(at(2026, 9, 30, 23, 30).toInstant());
        assertClosing(() -> close(SEPTEMBER, null), ClosingErrorCode.CLOSING_MONTH_NOT_ENDED);
        // 2026-10-01 00:00 KST (= 9/30 15:00 UTC) — 9월이 끝났다
        clock.setInstant(at(2026, 10, 1, 0, 0).toInstant());
        assertThat(close(SEPTEMBER, null).yearMonth()).isEqualTo(SEPTEMBER);
        assertClosing(() -> close(YearMonth.of(2026, 11), null), ClosingErrorCode.CLOSING_MONTH_NOT_ENDED);
        assertClosing(() -> close(SEPTEMBER, null), ClosingErrorCode.MONTH_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("기존 발송 월은 정정 여부 없이 마감할 수 없고, 겹치는 이전 기존 발송 월이 남았으면 다음 월을 먼저 잠글 수 없다")
    void legacySentMonthTransition() {
        ReportDispatch sentAugust = ReportDispatch.claim(AUGUST, clock.instant());
        sentAugust.markSent(clock.instant());
        reportDispatchRepository.save(sentAugust);

        assertThat(monthlyClosingService.getStatus(AUGUST).legacyDispatch()).isTrue();
        assertThat(monthlyClosingService.getStatus(SEPTEMBER).blockers())
                .containsExactly(ClosingBlocker.PREVIOUS_LEGACY_MONTH_PENDING);
        assertClosing(() -> close(SEPTEMBER, null), ClosingErrorCode.CLOSING_PREVIOUS_LEGACY_MONTH_PENDING);
        assertClosing(() -> close(AUGUST, null), ClosingErrorCode.LEGACY_RESOLUTION_REQUIRED);

        MonthlyClosingResponse august = monthlyClosingService.close(managerId,
                new MonthlyClosingCreateRequest(AUGUST, LegacyResolution.NO_CORRECTION_NEEDED, "8월 발송본 확인"));
        assertThat(august.type()).isEqualTo(MonthlyClosingType.LEGACY_CONFIRMED);
        assertClosing(() -> monthlyClosingService.close(managerId,
                        new MonthlyClosingCreateRequest(SEPTEMBER, LegacyResolution.CORRECTED, "x")),
                ClosingErrorCode.LEGACY_RESOLUTION_NOT_APPLICABLE);
        assertThat(close(SEPTEMBER, null).type()).isEqualTo(MonthlyClosingType.MANUAL);
    }

    @Test
    @DisplayName("수신 불명(DELIVERY_COMMITTED) 기존 발송 월은 확인 전 정정과 마감을 임시 차단한다")
    void deliveryUncertainMonthBlocksCorrectionAndClosing() {
        ReportDispatch uncertain = ReportDispatch.claim(AUGUST, clock.instant());
        uncertain.commitDelivery(clock.instant());
        reportDispatchRepository.save(uncertain);
        CommuteHistory aug10 = saveOpen(memberId, at(2026, 8, 10, 9, 0));

        assertClosing(() -> submit(memberId, aug10, at(2026, 8, 10, 18, 0)),
                ClosingErrorCode.REPORT_DELIVERY_UNCERTAIN);
        assertThat(monthlyClosingService.getStatus(AUGUST).blockers()).contains(ClosingBlocker.DELIVERY_UNCERTAIN);
    }

    // ---- helpers ----

    private Long saveEmployee(String name, Role role, String code) {
        Employee employee = new EmployeeBuilder()
                .withName(name)
                .withRole(role)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode(code)
                .withEmail(code.toLowerCase() + "@company.com")
                .withPassword("password123")
                .build();
        return employeeRepository.save(employee).getEmployeeId();
    }

    private CommuteHistory saveOpen(Long employeeId, ZonedDateTime start) {
        return commuteHistoryRepository.save(CommuteHistoryFixture.open(null, employeeId, start, KST));
    }

    private CommuteHistory reload(CommuteHistory history) {
        return commuteHistoryRepository.findById(history.getCommuteHistoryId()).orElseThrow();
    }

    private CommuteHistory latestOf(Long employeeId) {
        return commuteHistoryRepository.findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(employeeId)
                .orElseThrow();
    }

    private CorrectionRequestResponse submit(Long requesterId, CommuteHistory target, ZonedDateTime requestedEnd) {
        return correctionService.submit(requesterId, new CorrectionCreateRequest(
                target.getCommuteHistoryId(), target.getVersion(), requestedEnd.toOffsetDateTime(), "퇴근 누락"));
    }

    private MonthlyClosingResponse close(YearMonth yearMonth, LegacyResolution resolution) {
        return monthlyClosingService.close(managerId, new MonthlyClosingCreateRequest(yearMonth, resolution, null));
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, KST);
    }

    private static void assertCorrection(ThrowingCallable call, CorrectionErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(CorrectionException.class)
                .extracting(e -> ((CorrectionException) e).getCode())
                .isEqualTo(code);
    }

    private static void assertClosing(ThrowingCallable call, ClosingErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(ClosingException.class)
                .extracting(e -> ((ClosingException) e).getCode())
                .isEqualTo(code);
    }
}

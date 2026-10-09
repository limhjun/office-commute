package com.company.officecommute.service.closing;

import com.company.officecommute.domain.closing.ClosingErrorCode;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.closing.MonthlyClosing;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.domain.correction.CommuteCorrectionRequest;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.dto.closing.request.MonthlyClosingCreateRequest;
import com.company.officecommute.dto.closing.response.MonthlyClosingResponse;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse.ClosingBlocker;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse.PendingCorrectionSummary;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse.UnresolvedCommute;
import com.company.officecommute.dto.employee.response.EmployeeRef;
import com.company.officecommute.repository.closing.MonthlyClosingRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.service.commute.CommuteWriteLock;
import com.company.officecommute.service.overtime.OverTimePeriod;
import com.company.officecommute.service.report.OverTimeReportDispatchService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 월 마감. 집계 입력 기간({@link OverTimePeriod#rangeStart()}~월말)에 미퇴근·승인 대기가 없을 때만 잠근다.
 * <p>
 * 마감 트랜잭션은 전체 직원 행을 먼저 잠근다 — 모든 근태 쓰기가 소유 직원 행을 잠그므로, 검사 직후 끼어든
 * 신청·승인·출퇴근은 마감 커밋 뒤에 실행되어 보호 기간 검사에 걸린다. 보고서 생성·발송은 공휴일 API 와
 * SMTP 를 쓰므로 이 트랜잭션 밖(커밋 후)에서 한다.
 */
@Service
public class MonthlyClosingService {

    /** 회사 달력. 기존 보고서 스케줄과 같은 월 경계를 쓴다. 개인 근태의 workDate 는 재해석하지 않는다. */
    private static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Seoul");

    private final MonthlyClosingRepository monthlyClosingRepository;
    private final ReportDispatchRepository reportDispatchRepository;
    private final CommuteHistoryRepository commuteHistoryRepository;
    private final CommuteCorrectionRequestRepository correctionRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CommuteWriteLock commuteWriteLock;
    private final OverTimeReportDispatchService dispatchService;
    private final Clock clock;

    public MonthlyClosingService(
            MonthlyClosingRepository monthlyClosingRepository,
            ReportDispatchRepository reportDispatchRepository,
            CommuteHistoryRepository commuteHistoryRepository,
            CommuteCorrectionRequestRepository correctionRequestRepository,
            EmployeeRepository employeeRepository,
            CommuteWriteLock commuteWriteLock,
            OverTimeReportDispatchService dispatchService,
            Clock clock
    ) {
        this.monthlyClosingRepository = monthlyClosingRepository;
        this.reportDispatchRepository = reportDispatchRepository;
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.correctionRequestRepository = correctionRequestRepository;
        this.employeeRepository = employeeRepository;
        this.commuteWriteLock = commuteWriteLock;
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MonthlyClosingResponse> findAll() {
        List<MonthlyClosing> closings = monthlyClosingRepository.findAllByOrderByTargetYearMonthDesc();
        Map<Long, EmployeeRef> refs = employeeRefs(closings.stream().map(MonthlyClosing::getClosedById).collect(Collectors.toSet()));
        return closings.stream()
                .map(closing -> MonthlyClosingResponse.from(closing, refs.get(closing.getClosedById())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyClosingStatusResponse getStatus(YearMonth yearMonth) {
        Assessment assessment = assess(yearMonth);
        OverTimePeriod period = new OverTimePeriod(yearMonth);
        return new MonthlyClosingStatusResponse(
                yearMonth,
                period.rangeStart(),
                period.rangeEnd(),
                assessment.blockers().isEmpty(),
                assessment.closing().map(this::toResponse).orElse(null),
                assessment.legacyDispatch(),
                assessment.blockers(),
                assessment.unclosedCommutes(),
                assessment.pendingCorrections(),
                dispatchService.describeAll(yearMonth)
        );
    }

    @Transactional
    public MonthlyClosingResponse close(Long closedById, MonthlyClosingCreateRequest request) {
        commuteWriteLock.lockAllEmployees();
        YearMonth yearMonth = request.yearMonth();
        Assessment assessment = assess(yearMonth);
        List<ClosingBlocker> blockers = assessment.blockers();

        if (blockers.contains(ClosingBlocker.MONTH_NOT_ENDED)) {
            throw new ClosingException(ClosingErrorCode.CLOSING_MONTH_NOT_ENDED);
        }
        if (blockers.contains(ClosingBlocker.ALREADY_CLOSED)) {
            throw new ClosingException(ClosingErrorCode.MONTH_ALREADY_CLOSED);
        }
        MonthlyClosingType type = resolveType(assessment.legacyDispatch(), request);
        if (blockers.contains(ClosingBlocker.DELIVERY_UNCERTAIN)) {
            throw new ClosingException(ClosingErrorCode.REPORT_DELIVERY_UNCERTAIN,
                    "기존 발송의 수신 여부가 확인되지 않았습니다. 수신 여부를 먼저 확인해 주세요.");
        }
        if (blockers.contains(ClosingBlocker.PREVIOUS_LEGACY_MONTH_PENDING)) {
            throw new ClosingException(ClosingErrorCode.CLOSING_PREVIOUS_LEGACY_MONTH_PENDING,
                    "%s 은(는) 이미 발송된 월인데 아직 정리되지 않았고 집계 기간이 겹칩니다. 먼저 정리해 주세요."
                            .formatted(yearMonth.minusMonths(1)));
        }
        if (blockers.contains(ClosingBlocker.UNCLOSED_COMMUTES) || blockers.contains(ClosingBlocker.PENDING_CORRECTIONS)) {
            throw new ClosingException(ClosingErrorCode.CLOSING_HAS_UNRESOLVED,
                    "집계 기간에 미퇴근 %d건, 승인 대기 정정 %d건이 남아 있습니다."
                            .formatted(assessment.unclosedCommutes().size(), assessment.pendingCorrections().size()));
        }

        OverTimePeriod period = new OverTimePeriod(yearMonth);
        MonthlyClosing closing = new MonthlyClosing(
                yearMonth, type, period.rangeStart(), period.rangeEnd(), closedById, clock.instant(), request.note());
        try {
            return toResponse(monthlyClosingRepository.saveAndFlush(closing));
        } catch (DataIntegrityViolationException e) {
            throw new ClosingException(ClosingErrorCode.MONTH_ALREADY_CLOSED, e);
        }
    }

    /**
     * 기능 도입 전에 원본이 이미 발송된 월은 운영자가 정정 여부를 밝혀야 한다 — 마감 정보가 비어 있다는
     * 이유로 일반 미마감 월처럼 마감해 원본을 다시 보내면 안 된다.
     */
    private static MonthlyClosingType resolveType(boolean legacyDispatch, MonthlyClosingCreateRequest request) {
        if (!legacyDispatch) {
            if (request.legacyResolution() != null) {
                throw new ClosingException(ClosingErrorCode.LEGACY_RESOLUTION_NOT_APPLICABLE);
            }
            return MonthlyClosingType.MANUAL;
        }
        if (request.legacyResolution() == null || request.note() == null || request.note().isBlank()) {
            throw new ClosingException(ClosingErrorCode.LEGACY_RESOLUTION_REQUIRED);
        }
        return request.legacyResolution().closingType();
    }

    private Assessment assess(YearMonth yearMonth) {
        OverTimePeriod period = new OverTimePeriod(yearMonth);
        YearMonth currentMonth = YearMonth.now(clock.withZone(COMPANY_ZONE));
        Optional<MonthlyClosing> closing = monthlyClosingRepository.findByTargetYearMonth(yearMonth);
        Optional<DispatchStatus> originalStatus = reportDispatchRepository.findByTargetYearMonth(yearMonth)
                .map(ReportDispatch::getStatus);

        List<UnresolvedCommute> unclosedCommutes = commuteHistoryRepository
                .findUnresolvedByWorkDateBetween(period.rangeStart(), period.rangeEnd())
                .stream()
                .map(row -> new UnresolvedCommute(
                        row.commuteHistoryId(),
                        new EmployeeRef(row.employeeId(), row.employeeName(), row.employeeCode()),
                        row.workDate()))
                .toList();
        List<PendingCorrectionSummary> pendingCorrections = pendingCorrections(period);

        List<ClosingBlocker> blockers = new ArrayList<>();
        if (!yearMonth.isBefore(currentMonth)) {
            blockers.add(ClosingBlocker.MONTH_NOT_ENDED);
        }
        if (closing.isPresent()) {
            blockers.add(ClosingBlocker.ALREADY_CLOSED);
        }
        if (!unclosedCommutes.isEmpty()) {
            blockers.add(ClosingBlocker.UNCLOSED_COMMUTES);
        }
        if (!pendingCorrections.isEmpty()) {
            blockers.add(ClosingBlocker.PENDING_CORRECTIONS);
        }
        if (closing.isEmpty() && originalStatus.filter(status -> status == DispatchStatus.DELIVERY_COMMITTED).isPresent()) {
            blockers.add(ClosingBlocker.DELIVERY_UNCERTAIN);
        }
        if (previousLegacyMonthPending(yearMonth, period)) {
            blockers.add(ClosingBlocker.PREVIOUS_LEGACY_MONTH_PENDING);
        }
        boolean legacyDispatch = closing.isEmpty()
                && originalStatus.filter(status -> status == DispatchStatus.SENT).isPresent();
        return new Assessment(closing, legacyDispatch, List.copyOf(blockers), unclosedCommutes, pendingCorrections);
    }

    /**
     * 이 달의 입력 기간이 전월 말을 포함하고, 전월이 마감 없이 이미 발송된 기존 월이면 전월부터 정리해야 한다.
     * 먼저 이 달을 잠그면 전월 말 기록의 정정까지 막히기 때문이다.
     */
    private boolean previousLegacyMonthPending(YearMonth yearMonth, OverTimePeriod period) {
        if (!period.rangeStart().isBefore(yearMonth.atDay(1))) {
            return false;
        }
        YearMonth previous = yearMonth.minusMonths(1);
        if (monthlyClosingRepository.existsByTargetYearMonth(previous)) {
            return false;
        }
        return reportDispatchRepository.findByTargetYearMonth(previous)
                .map(ReportDispatch::isDeliveryFinalized)
                .orElse(false);
    }

    private List<PendingCorrectionSummary> pendingCorrections(OverTimePeriod period) {
        List<CommuteCorrectionRequest> pending = correctionRequestRepository
                .findAllByStatusAndWorkDateBetweenOrderByWorkDateAscCorrectionRequestIdAsc(
                        CorrectionStatus.PENDING, period.rangeStart(), period.rangeEnd());
        Map<Long, EmployeeRef> refs = employeeRefs(pending.stream()
                .map(CommuteCorrectionRequest::getRequesterId)
                .collect(Collectors.toSet()));
        return pending.stream()
                .map(request -> new PendingCorrectionSummary(
                        request.getCorrectionRequestId(),
                        request.getCommuteHistoryId(),
                        refs.get(request.getRequesterId()),
                        request.getWorkDate()))
                .toList();
    }

    private MonthlyClosingResponse toResponse(MonthlyClosing closing) {
        EmployeeRef closedBy = employeeRepository.findById(closing.getClosedById())
                .map(EmployeeRef::from)
                .orElse(null);
        return MonthlyClosingResponse.from(closing, closedBy);
    }

    private Map<Long, EmployeeRef> employeeRefs(Set<Long> employeeIds) {
        return employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, EmployeeRef::from));
    }

    private record Assessment(
            Optional<MonthlyClosing> closing,
            boolean legacyDispatch,
            List<ClosingBlocker> blockers,
            List<UnresolvedCommute> unclosedCommutes,
            List<PendingCorrectionSummary> pendingCorrections
    ) {
    }
}

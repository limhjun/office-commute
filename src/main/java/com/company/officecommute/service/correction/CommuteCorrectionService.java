package com.company.officecommute.service.correction;

import com.company.officecommute.auth.ForbiddenException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.correction.CommuteCorrectionRequest;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.correction.CorrectionReviewPolicy;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.correction.request.CorrectionCreateRequest;
import com.company.officecommute.dto.correction.response.CorrectionRequestResponse;
import com.company.officecommute.dto.employee.response.EmployeeRef;
import com.company.officecommute.global.persistence.DatabaseConstraintMatcher;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.closing.CommutePeriodGuard;
import com.company.officecommute.service.commute.CommuteWriteLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 퇴근 시각 정정 요청의 신청·승인·반려·취소.
 * <p>
 * 모든 쓰기는 신청자(근태 소유자)의 직원 행을 먼저 잠근다({@link CommuteWriteLock}). 같은 직원의 출퇴근·다른
 * 정정 처리·월 마감과 직렬화되므로, 승인 트랜잭션 안의 재검증(권한·상태·원본 버전·마감 범위·후속 근무)이
 * 커밋 시점까지 유효하다. 원본 갱신과 요청 처리는 한 트랜잭션이며 하나라도 실패하면 모두 롤백된다.
 */
@Service
public class CommuteCorrectionService {

    private static final String UK_CORRECTION_REQUEST_PENDING = "uk_correction_request_pending";

    private final CommuteCorrectionRequestRepository correctionRequestRepository;
    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final CommuteWriteLock commuteWriteLock;
    private final CommutePeriodGuard commutePeriodGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CommuteCorrectionService(
            CommuteCorrectionRequestRepository correctionRequestRepository,
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            CommuteWriteLock commuteWriteLock,
            CommutePeriodGuard commutePeriodGuard,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.commuteWriteLock = commuteWriteLock;
        this.commutePeriodGuard = commutePeriodGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional
    public CorrectionRequestResponse submit(Long requesterId, CorrectionCreateRequest request) {
        commuteWriteLock.lockEmployee(requesterId);
        Employee requester = getEmployee(requesterId);
        CommuteHistory commute = getCommute(request.commuteHistoryId());
        if (!Objects.equals(commute.getEmployeeId(), requesterId)) {
            throw new ForbiddenException();
        }
        if (commute.getVersion() != request.commuteVersion()) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_VERSION_CONFLICT);
        }
        commutePeriodGuard.assertWritable(commute.getWorkDate());
        if (correctionRequestRepository.existsByPendingCommuteHistoryId(commute.getCommuteHistoryId())) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_ALREADY_PENDING);
        }
        Long assignedApproverId = resolveAssignedApprover(requester);

        Instant now = clock.instant();
        // 저장 정밀도를 초 단위로 고정해 화면 입력·DB(DATETIME(6))·근무 분 계산이 같은 값을 보게 한다.
        Instant requestedEnd = request.requestedWorkEndTime().toInstant().truncatedTo(ChronoUnit.SECONDS);
        long requestedMinutes = commute.calculateCorrectedWorkingMinutes(
                requestedEnd, now, nextActualWorkStart(commute));

        CommuteCorrectionRequest saved = savePending(CommuteCorrectionRequest.submit(
                commute, requester.getRole(), assignedApproverId, requestedEnd, requestedMinutes, request.reason(), now));
        return toResponses(List.of(saved), requester).getFirst();
    }

    /**
     * 신청 당시 지정 승인자 스냅샷. 담당자가 없어도 신청은 받는다(계획 1.3: 담당자가 없으면 "승인할 수 없다") —
     * 승인·반려 시점에 {@link CorrectionReviewPolicy}가 CORRECTION_APPROVER_NOT_ASSIGNED 로 막는다.
     * 대기 중에는 담당자를 바꿀 수 없으므로 처리하려면 취소 → 지정 → 재신청 순서를 따른다.
     */
    private Long resolveAssignedApprover(Employee requester) {
        if (requester.getRole().requiredCorrectionApproverRole() == null) {
            return null;
        }
        return requester.getCorrectionApproverId();
    }

    private CommuteCorrectionRequest savePending(CommuteCorrectionRequest request) {
        try {
            return correctionRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            if (DatabaseConstraintMatcher.matches(e, UK_CORRECTION_REQUEST_PENDING)) {
                throw new CorrectionException(CorrectionErrorCode.CORRECTION_ALREADY_PENDING, e);
            }
            throw e;
        }
    }

    /**
     * 승인. 신청 이후 바뀌었을 수 있는 모든 조건을 다시 검사한다. 원본 충돌은 요청을 REJECTED 로 바꾸지 않고
     * PENDING 으로 둔 채 409 를 돌려준다 — 업무상 반려와 기술적 충돌을 섞지 않는다.
     */
    public CorrectionRequestResponse approve(Long reviewerId, Long requestId, String comment) {
        Long requesterId = findRequesterId(requestId);
        return transactionTemplate.execute(status -> {
            commuteWriteLock.lockEmployee(requesterId);
            CommuteCorrectionRequest request = getRequest(requestId);
            Employee reviewer = getEmployee(reviewerId);
            Employee requester = getEmployee(requesterId);
            CorrectionReviewPolicy.authorize(reviewer, requester, request);
            request.ensurePending();

            CommuteHistory commute = getCommute(request.getCommuteHistoryId());
            // 다른 기록의 퇴근은 이 기록의 버전을 바꾸지 않는다 — 같은 행이 바뀐 경우만 충돌이다.
            if (commute.getVersion() != request.getCommuteVersion()) {
                throw new CorrectionException(CorrectionErrorCode.CORRECTION_VERSION_CONFLICT);
            }
            commutePeriodGuard.assertWritable(commute.getWorkDate());
            Instant now = clock.instant();
            // 버전이 같아도 신청 이후 새 출근이 생겼다면 후속 근무 경계가 달라졌을 수 있다.
            long workingMinutes = commute.calculateCorrectedWorkingMinutes(
                    request.getRequestedWorkEndTime(), now, nextActualWorkStart(commute));

            request.approve(reviewerId, comment, now);
            flushTransition(request);
            // 응답에 쓸 직원 정보는 bulk UPDATE 의 clear 전에 읽어 둔다.
            List<CorrectionRequestResponse> response = toResponses(List.of(request), reviewer);

            int updated = commuteHistoryRepository.updateWorkEndTimeIfVersion(
                    commute.getCommuteHistoryId(), request.getCommuteVersion(),
                    request.getRequestedWorkEndTime(), workingMinutes);
            if (updated == 0) {
                // 요청 상태 전이까지 함께 롤백된다.
                throw new CorrectionException(CorrectionErrorCode.CORRECTION_VERSION_CONFLICT);
            }
            return response.getFirst();
        });
    }

    public CorrectionRequestResponse reject(Long reviewerId, Long requestId, String reason) {
        Long requesterId = findRequesterId(requestId);
        return transactionTemplate.execute(status -> {
            commuteWriteLock.lockEmployee(requesterId);
            CommuteCorrectionRequest request = getRequest(requestId);
            Employee reviewer = getEmployee(reviewerId);
            Employee requester = getEmployee(requesterId);
            CorrectionReviewPolicy.authorize(reviewer, requester, request);

            request.reject(reviewerId, reason, clock.instant());
            flushTransition(request);
            return toResponses(List.of(request), reviewer).getFirst();
        });
    }

    public CorrectionRequestResponse cancel(Long actorId, Long requestId) {
        Long requesterId = findRequesterId(requestId);
        return transactionTemplate.execute(status -> {
            commuteWriteLock.lockEmployee(requesterId);
            CommuteCorrectionRequest request = getRequest(requestId);
            if (!request.isRequestedBy(actorId)) {
                throw new ForbiddenException();
            }
            request.cancel(actorId, clock.instant());
            flushTransition(request);
            return toResponses(List.of(request), getEmployee(actorId)).getFirst();
        });
    }

    private void flushTransition(CommuteCorrectionRequest request) {
        try {
            correctionRequestRepository.saveAndFlush(request);
        } catch (OptimisticLockingFailureException e) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_ALREADY_PROCESSED, e);
        }
    }

    @Transactional(readOnly = true)
    public List<CorrectionRequestResponse> findMine(Long employeeId, CorrectionStatus status) {
        Employee viewer = getEmployee(employeeId);
        return toResponses(filterByStatus(
                correctionRequestRepository.findAllByRequesterIdOrderByRequestedAtDescCorrectionRequestIdDesc(employeeId),
                status), viewer);
    }

    @Transactional(readOnly = true)
    public List<CorrectionRequestResponse> findReviewScope(Long reviewerId, CorrectionStatus status) {
        Employee viewer = getEmployee(reviewerId);
        List<CommuteCorrectionRequest> requests = switch (viewer.getRole()) {
            case MANAGER -> correctionRequestRepository.findManagerReviewScope(reviewerId, Role.MEMBER);
            case COMMUTE_APPROVER -> correctionRequestRepository
                    .findAllByAssignedApproverIdAndRequesterIdNotOrderByRequestedAtDescCorrectionRequestIdDesc(
                            reviewerId, reviewerId);
            case MEMBER -> throw new ForbiddenException();
        };
        return toResponses(filterByStatus(requests, status), viewer);
    }

    @Transactional(readOnly = true)
    public CorrectionRequestResponse findOne(Long viewerId, Long requestId) {
        Employee viewer = getEmployee(viewerId);
        CommuteCorrectionRequest request = getRequest(requestId);
        if (!CorrectionReviewPolicy.canView(viewer, request)) {
            throw new ForbiddenException();
        }
        return toResponses(List.of(request), viewer).getFirst();
    }

    private static List<CommuteCorrectionRequest> filterByStatus(
            List<CommuteCorrectionRequest> requests,
            CorrectionStatus status
    ) {
        if (status == null) {
            return requests;
        }
        return requests.stream()
                .filter(request -> request.getStatus() == status)
                .toList();
    }

    private List<CorrectionRequestResponse> toResponses(List<CommuteCorrectionRequest> requests, Employee viewer) {
        Map<Long, Employee> employees = loadEmployees(requests);
        Function<Long, EmployeeRef> refs = employeeId -> EmployeeRef.from(employees.get(employeeId));
        return requests.stream()
                .map(request -> CorrectionRequestResponse.of(request, refs, actionsFor(request, viewer, employees)))
                .toList();
    }

    private Map<Long, Employee> loadEmployees(Collection<CommuteCorrectionRequest> requests) {
        Set<Long> ids = new HashSet<>();
        for (CommuteCorrectionRequest request : requests) {
            ids.add(request.getRequesterId());
            if (request.getAssignedApproverId() != null) {
                ids.add(request.getAssignedApproverId());
            }
            if (request.getProcessedById() != null) {
                ids.add(request.getProcessedById());
            }
        }
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));
    }

    private static CorrectionRequestResponse.Actions actionsFor(
            CommuteCorrectionRequest request,
            Employee viewer,
            Map<Long, Employee> employees
    ) {
        if (!request.isPending()) {
            return new CorrectionRequestResponse.Actions(false, false);
        }
        Employee requester = employees.get(request.getRequesterId());
        boolean canReview = requester != null && CorrectionReviewPolicy.canReview(viewer, requester, request);
        return new CorrectionRequestResponse.Actions(request.isRequestedBy(viewer.getEmployeeId()), canReview);
    }

    private Instant nextActualWorkStart(CommuteHistory commute) {
        return commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkStartTimeAfterOrderByWorkStartTimeAsc(
                        commute.getEmployeeId(), commute.getWorkStartTime())
                .map(CommuteHistory::getWorkStartTime)
                .orElse(null);
    }

    private Long findRequesterId(Long requestId) {
        return correctionRequestRepository.findRequesterIdById(requestId)
                .orElseThrow(() -> new CorrectionException(CorrectionErrorCode.CORRECTION_REQUEST_NOT_FOUND));
    }

    private CommuteCorrectionRequest getRequest(Long requestId) {
        return correctionRequestRepository.findById(requestId)
                .orElseThrow(() -> new CorrectionException(CorrectionErrorCode.CORRECTION_REQUEST_NOT_FOUND));
    }

    private CommuteHistory getCommute(Long commuteHistoryId) {
        return commuteHistoryRepository.findById(commuteHistoryId)
                .orElseThrow(() -> new CorrectionException(CorrectionErrorCode.COMMUTE_NOT_FOUND));
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}

package com.company.officecommute.domain.correction;

import com.company.officecommute.auth.ForbiddenException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;

import java.util.Objects;

/**
 * 정정 요청 처리(승인·반려) 권한.
 * <pre>
 * MEMBER 요청           → MANAGER
 * MANAGER 요청          → 지정된 COMMUTE_APPROVER
 * COMMUTE_APPROVER 요청 → 지정된 MANAGER
 * </pre>
 * 역할과 지정 관계는 처리 시점의 현재 값으로 판단한다. 대기 요청이 있는 동안 지정·역할 변경이 막혀 있으므로
 * 신청 당시 스냅샷과 어긋나지 않는다.
 */
public final class CorrectionReviewPolicy {

    private CorrectionReviewPolicy() {
    }

    public static void authorize(Employee reviewer, Employee requester) {
        if (Objects.equals(reviewer.getEmployeeId(), requester.getEmployeeId())) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_SELF_APPROVAL);
        }
        Role requiredApproverRole = requester.getRole().requiredCorrectionApproverRole();
        if (requiredApproverRole == null) {
            if (reviewer.getRole() != Role.MANAGER) {
                throw new ForbiddenException();
            }
            return;
        }
        Long assignedApproverId = requester.getCorrectionApproverId();
        if (assignedApproverId == null) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_APPROVER_NOT_ASSIGNED);
        }
        if (!assignedApproverId.equals(reviewer.getEmployeeId()) || reviewer.getRole() != requiredApproverRole) {
            throw new ForbiddenException();
        }
    }

    public static boolean canReview(Employee reviewer, Employee requester) {
        try {
            authorize(reviewer, requester);
            return true;
        } catch (CorrectionException | ForbiddenException e) {
            return false;
        }
    }

    /**
     * 조회 범위. 신청 당시 스냅샷으로 판단해, 처리한 이력도 계속 볼 수 있게 한다.
     */
    public static boolean canView(Employee viewer, CommuteCorrectionRequest request) {
        if (request.isRequestedBy(viewer.getEmployeeId())) {
            return true;
        }
        if (Objects.equals(request.getAssignedApproverId(), viewer.getEmployeeId())) {
            return true;
        }
        return viewer.getRole() == Role.MANAGER && request.getRequesterRole() == Role.MEMBER;
    }
}

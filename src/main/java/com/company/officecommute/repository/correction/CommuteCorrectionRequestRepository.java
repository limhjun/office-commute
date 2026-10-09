package com.company.officecommute.repository.correction;

import com.company.officecommute.domain.correction.CommuteCorrectionRequest;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommuteCorrectionRequestRepository extends JpaRepository<CommuteCorrectionRequest, Long> {

    /**
     * 신청자 ID 는 바뀌지 않으므로 잠금 전에 읽어도 된다. 잠글 직원 행을 정하는 데만 쓴다.
     */
    @Query("""
            SELECT r.requesterId
            FROM CommuteCorrectionRequest r
            WHERE r.correctionRequestId = :correctionRequestId
            """)
    Optional<Long> findRequesterIdById(Long correctionRequestId);

    List<CommuteCorrectionRequest> findAllByRequesterIdOrderByRequestedAtDescCorrectionRequestIdDesc(Long requesterId);

    /** MANAGER 의 처리 범위: MEMBER 요청 전체 + 본인이 지정 승인자인 요청. 본인 요청은 제외. */
    @Query("""
            SELECT r
            FROM CommuteCorrectionRequest r
            WHERE r.requesterId <> :managerId
                AND (r.requesterRole = :memberRole OR r.assignedApproverId = :managerId)
            ORDER BY r.requestedAt DESC, r.correctionRequestId DESC
            """)
    List<CommuteCorrectionRequest> findManagerReviewScope(Long managerId, Role memberRole);

    List<CommuteCorrectionRequest> findAllByAssignedApproverIdAndRequesterIdNotOrderByRequestedAtDescCorrectionRequestIdDesc(
            Long assignedApproverId,
            Long requesterId
    );

    List<CommuteCorrectionRequest> findAllByPendingCommuteHistoryIdIn(Collection<Long> commuteHistoryIds);

    boolean existsByPendingCommuteHistoryId(Long commuteHistoryId);

    boolean existsByRequesterIdAndStatus(Long requesterId, CorrectionStatus status);

    boolean existsByAssignedApproverIdAndStatus(Long assignedApproverId, CorrectionStatus status);

    /** 입력 기간 기록에 대한 대기 요청. workDate 는 원본 근무일 스냅샷이며 원본과 함께 바뀌지 않는다. */
    List<CommuteCorrectionRequest> findAllByStatusAndWorkDateBetweenOrderByWorkDateAscCorrectionRequestIdAsc(
            CorrectionStatus status,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByStatusAndWorkDateBetween(CorrectionStatus status, LocalDate startDate, LocalDate endDate);
}

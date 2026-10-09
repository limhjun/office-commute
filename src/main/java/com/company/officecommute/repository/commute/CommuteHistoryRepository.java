package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.service.closing.UnresolvedCommuteRow;
import com.company.officecommute.service.overtime.DailyWorkingMinutes;
import com.company.officecommute.service.overtime.UnclosedCommute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommuteHistoryRepository extends JpaRepository<CommuteHistory, Long> {

    Optional<CommuteHistory> findFirstByEmployeeIdOrderByWorkStartTimeDesc(Long employeeId);

    /** 가장 최근에 시작한 실제 근무(연차 제외). 일반 퇴근의 유일한 대상이자 후속 근무 판정의 기준이다. */
    Optional<CommuteHistory> findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(Long employeeId);

    /** 이 출근 시각 이후에 시작한 첫 실제 근무. 정정 종료 시각의 상한이다. */
    Optional<CommuteHistory> findFirstByEmployeeIdAndUsingDayOffFalseAndWorkStartTimeAfterOrderByWorkStartTimeAsc(
            Long employeeId,
            Instant workStartTime
    );

    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<CommuteHistory> findAllByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    /**
     * 해당 기간의 미마감 기록 목록. 관리자에게 "무엇을 마감해야 하는지"를 알리고,
     * 미마감 건수도 이 목록의 크기로 계산한다.
     * <p>
     * LEFT JOIN인 이유: 이 목록은 대표 발송을 막는 게이트다. 직원 행이 없는(잘못된 데이터)
     * 미마감 기록이 조인에서 탈락하면 게이트가 조용히 좁아진다 — 그런 기록일수록 드러나야 한다.
     */
    @Query("""
            SELECT new com.company.officecommute.service.overtime.UnclosedCommute(
                        COALESCE(e.employeeCode, '(직원 정보 없음)'),
                        COALESCE(e.name, '(직원 정보 없음)'),
                        ch.workDate
                    )
            FROM CommuteHistory ch
                LEFT JOIN Employee e ON e.employeeId = ch.employeeId
            WHERE ch.workDate BETWEEN :startDate AND :endDate
                AND ch.workEndTime IS NULL
            ORDER BY ch.workDate, e.employeeCode
            """)
    List<UnclosedCommute> findUnclosedByWorkDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * 입력 기간의 미퇴근 기록. 월 마감 차단 사유로 화면에 보여 주므로 기록 ID 가 필요하다.
     * LEFT JOIN 인 이유는 {@link #findUnclosedByWorkDateBetween}과 같다.
     */
    @Query("""
            SELECT new com.company.officecommute.service.closing.UnresolvedCommuteRow(
                        ch.commuteHistoryId,
                        ch.employeeId,
                        COALESCE(e.employeeCode, '(직원 정보 없음)'),
                        COALESCE(e.name, '(직원 정보 없음)'),
                        ch.workDate
                    )
            FROM CommuteHistory ch
                LEFT JOIN Employee e ON e.employeeId = ch.employeeId
            WHERE ch.workDate BETWEEN :startDate AND :endDate
                AND ch.workEndTime IS NULL
            ORDER BY ch.workDate, e.employeeCode
            """)
    List<UnresolvedCommuteRow> findUnresolvedByWorkDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * 일반 퇴근. {@code workEndTime IS NULL} 조건으로 상태 확인과 변경을 단일 UPDATE로 묶어,
     * 동시 퇴근 요청 중 정확히 한 건만 성공한다(나머지는 0 반환).
     * bulk UPDATE 는 {@code @Version}을 올리지 않으므로 직접 올린다 — 이 기록을 겨냥한 정정 대기 요청이
     * 승인 시 원본 변경을 알아챈다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CommuteHistory ch
            SET ch.workEndTime = :workEndTime,
                ch.workingMinutes = :workingMinutes,
                ch.version = ch.version + 1
            WHERE ch.commuteHistoryId = :commuteHistoryId
                AND ch.workEndTime IS NULL
            """)
    int updateWorkEndTimeIfOpen(Long commuteHistoryId, Instant workEndTime, long workingMinutes);

    /**
     * 정정 승인. 신청 당시 원본 버전과 같을 때만 종료 시각·근무 분을 바꾼다(0 반환 = 원본 변경 충돌).
     * 직원 ID·출근 시각·근무일·시간대·연차 여부는 건드리지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CommuteHistory ch
            SET ch.workEndTime = :workEndTime,
                ch.workingMinutes = :workingMinutes,
                ch.version = ch.version + 1
            WHERE ch.commuteHistoryId = :commuteHistoryId
                AND ch.version = :expectedVersion
                AND ch.usingDayOff = false
            """)
    int updateWorkEndTimeIfVersion(Long commuteHistoryId, long expectedVersion, Instant workEndTime, long workingMinutes);

    /**
     * 기간 내 직원별·일별 근무 분.
     * <p>
     * 초과근무는 1일 8시간·1주 40시간 기준으로 계산하므로 월 SUM이 아니라 일별 행이 필요하다.
     * work_date(직원 타임존 기준 확정값) 필터라 JVM 기본 타임존이 월 분류에 영향을 주지 않는다.
     * 연차·퇴근 미마감 기록은 workingMinutes = 0 행으로 나타난다.
     */
    @Query("""
            SELECT new com.company.officecommute.service.overtime.DailyWorkingMinutes(
                        ch.employeeId, ch.workDate, ch.workingMinutes
                    )
            FROM CommuteHistory ch
            WHERE ch.workDate BETWEEN :startDate AND :endDate
            """)
    List<DailyWorkingMinutes> findDailyWorkingMinutesByWorkDateBetween(LocalDate startDate, LocalDate endDate);
}

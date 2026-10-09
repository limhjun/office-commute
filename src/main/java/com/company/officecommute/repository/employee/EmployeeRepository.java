package com.company.officecommute.repository.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            LEFT JOIN FETCH e.correctionApprover
            """)
    List<Employee> findAllWithTeam();

    /**
     * 재직 기간이 [rangeStart, rangeEnd]와 겹치는 직원.
     * 기간 중 퇴사자는 포함(그 기간의 초과근무는 지급 대상), 기간 시작 전 퇴사자·기간 종료 후 입사자는 제외.
     */
    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            WHERE (e.workEndDate IS NULL OR e.workEndDate >= :rangeStart)
              AND e.workStartDate <= :rangeEnd
            """)
    List<Employee> findAllWithTeamEmployedBetween(
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            LEFT JOIN FETCH e.correctionApprover
            WHERE e.employeeId = :employeeId
            """)
    Optional<Employee> findByEmployeeIdWithTeam(@Param("employeeId") Long employeeId);

    /** 매 요청의 권한 판정용. 엔티티를 영속성 컨텍스트에 올리지 않도록 역할 값만 읽는다. */
    @Query("""
            SELECT e.role
            FROM Employee e
            WHERE e.employeeId = :employeeId
            """)
    Optional<Role> findRoleById(@Param("employeeId") Long employeeId);

    /**
     * 근태 쓰기 잠금 — 한 직원의 출퇴근·정정·연차 쓰기를 직렬화한다.
     * 트랜잭션의 첫 문장으로 불러야 이후 일관 읽기가 잠금 획득 뒤의 커밋을 본다.
     * 엔티티가 아닌 ID 만 읽어, 이미 관리 중인 엔티티의 낡은 상태를 돌려받는 일이 없게 한다.
     */
    @Query(value = "SELECT employee_id FROM employee WHERE employee_id = :employeeId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("employeeId") Long employeeId);

    /**
     * 전체 근태 쓰기 잠금 — 월 마감·퇴사·담당자 지정. 한 행만 잠그는 쓰기와 교착하지 않도록 항상 ID 오름차순이다.
     */
    @Query(value = "SELECT employee_id FROM employee ORDER BY employee_id FOR UPDATE", nativeQuery = true)
    List<Long> lockAll();

    @Query("""
            SELECT e.team.teamId, COUNT(e)
            FROM Employee e
            WHERE e.team.teamId IN :teamIds
            GROUP BY e.team.teamId
            """)
    List<Object[]> countMembersByTeamIdsRaw(@Param("teamIds") List<Long> teamIds);
}

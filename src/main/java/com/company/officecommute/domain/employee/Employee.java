package com.company.officecommute.domain.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveEnrollment;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import com.company.officecommute.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
public class Employee {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY) // 왜? LAZY?
    @JoinColumn(name= "team_id")
    private Team team;

    private String name;

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDate birthday;

    private LocalDate workStartDate;

    @Column(unique = true, nullable = false)
    private String employeeCode;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    protected Employee() {
    }

    public Employee(
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate
    ) {
        this(null, null, name, role, birthday, workStartDate, "TEST001", "test@example.com", "password");
    }

    public Employee(
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate,
            String employeeCode,
            String email,
            String password
    ) {
        this(null, null, name, role, birthday, workStartDate, employeeCode, email, password);
    }

    public Employee(
            Long employeeId,
            Team team,
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate,
            String employeeCode,
            String email,
            String password
    ) {
        this.employeeId = employeeId;
        this.team = team;
        this.name = validateName(name);
        this.role = Objects.requireNonNull(role, "role은 null일 수 없습니다");
        this.birthday = Objects.requireNonNull(birthday, "birthday는 null일 수 없습니다");
        this.workStartDate = Objects.requireNonNull(workStartDate, "workStartDate는 null일 수 없습니다");
        this.employeeCode = validateEmployeeCode(employeeCode);
        this.email = validateEmail(email);
        this.password = validatePassword(password);
    }

    private String validateEmployeeCode(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) {
            throw new IllegalArgumentException("employee의 employeeCode가 올바르지 않은 형식입니다.");
        }
        return employeeCode;
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("employee의 name이 올바르지 않은 형식입니다.");
        }
        return name.trim();
    }

    private String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email은 null이거나 빈 값일 수 없습니다.");
        }
        String trimmed = email.trim();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("email 형식이 올바르지 않습니다.");
        }
        return trimmed;
    }

    private String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password는 null이거나 빈 값일 수 없습니다.");
        }
        return password;
    }

    public boolean matchesPassword(String rawPassword) {
        return this.password.equals(rawPassword);
    }

    public void changeTeam(Team newTeam) {
        Team oldTeam = this.team;
        this.team = newTeam;
        if (oldTeam != null) {
            oldTeam.decreaseMemberCount();
        }
        if (newTeam != null) {
            newTeam.increaseMemberCount();
        }
    }

    public List<AnnualLeave> enrollAnnualLeave(List<LocalDate> wantedDates, List<AnnualLeave> existingAnnualLeaves) {
        if (team == null) {
            throw new IllegalStateException("팀이 배정되지 않은 직원은 연차를 신청할 수 없습니다.");
        }
        List<AnnualLeave> wantedLeaves = wantedDates.stream()
                .map(wantedDate -> new AnnualLeave(employeeId, wantedDate))
                .toList();
        if (team.isNotEnoughCriteria(wantedLeaves)) {
            throw new IllegalArgumentException("팀의 연차 등록 기준을 충족하지 못합니다.");
        }
        AnnualLeaveEnrollment enrollment = new AnnualLeaveEnrollment(employeeId, team, existingAnnualLeaves);
        AnnualLeaves annualLeaves = new AnnualLeaves(wantedLeaves);
        enrollment.enroll(annualLeaves);
        return annualLeaves.getAnnualLeaves();
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Team getTeam() {
        return team;
    }

    public String getName() {
        return name;
    }

    public String getTeamName() {
        return team != null ? team.getName() : null;
    }

    public Role getRole() {
        return role;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public LocalDate getWorkStartDate() {
        return workStartDate;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}

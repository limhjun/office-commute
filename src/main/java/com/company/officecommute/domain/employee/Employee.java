package com.company.officecommute.domain.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveCriteriaNotMetException;
import com.company.officecommute.domain.annual_leave.AnnualLeaveEnrollment;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import com.company.officecommute.domain.annual_leave.EmployeeWithoutTeamException;
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
import java.time.ZoneId;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false)
    private LocalDate workStartDate;

    @Column(unique = true, nullable = false)
    private String employeeCode;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String timezone;

    protected Employee() {
    }

    public static Employee register(
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate,
            String employeeCode,
            String email,
            String encodedPassword,
            String timezone,
            Team team
    ) {
        return new Employee(null, team, name, role, birthday, workStartDate, employeeCode, email, encodedPassword, timezone);
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
        this(null, null, name, role, birthday, workStartDate, employeeCode, email, password, "Asia/Seoul");
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
        this(employeeId, team, name, role, birthday, workStartDate, employeeCode, email, password, "Asia/Seoul");
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
            String password,
            String timezone
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
        this.timezone = validateTimezone(timezone);
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

    private String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone은 null이거나 빈 값일 수 없습니다.");
        }
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("timezone이 올바른 ZoneId 형식이 아닙니다: " + timezone);
        }
        return timezone;
    }

    public void changeTeam(Team newTeam) {
        this.team = newTeam;
    }

    public List<AnnualLeave> enrollAnnualLeave(List<LocalDate> wantedDates, List<AnnualLeave> existingAnnualLeaves) {
        if (team == null) {
            throw new EmployeeWithoutTeamException();
        }
        List<AnnualLeave> wantedLeaves = wantedDates.stream()
                .map(wantedDate -> new AnnualLeave(employeeId, wantedDate))
                .toList();
        if (team.isNotEnoughCriteria(wantedLeaves)) {
            throw new AnnualLeaveCriteriaNotMetException();
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

    public String getTimezone() {
        return timezone;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }
}

package com.company.officecommute.domain.team;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.List;

@Entity
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long teamId;

    @Column(nullable = false, unique = true)
    private String name;

    private String managerName;

    private int annualLeaveCriteria;

    protected Team() {
    }

    public Team(String name) {
        this(null, name, null, 0);
    }

    public Team(String name, String managerName) {
        this(null, name, managerName, 0);
    }

    public Team(Long teamId, String name, String managerName) {
        this(teamId, name, managerName, 0);
    }

    public Team(Long teamId, String name, String managerName, int annualLeaveCriteria) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", name));
        }
        this.teamId = teamId;
        this.name = name;
        this.managerName = managerName;
        this.annualLeaveCriteria = annualLeaveCriteria;
    }

    public static Team register(String name, String managerName) {
        String normalizedManager = (managerName == null || managerName.isBlank()) ? null : managerName.trim();
        return new Team(null, name, normalizedManager, 0);
    }

    public Long getTeamId() {
        return this.teamId;
    }

    public String getName() {
        return this.name;
    }

    public String getManagerName() {
        return this.managerName;
    }

    public boolean isNotEnoughCriteria(List<AnnualLeave> wantedLeaves) {
        return isNotEnoughCriteria(new AnnualLeaves(wantedLeaves));
    }

    public boolean isNotEnoughCriteria(AnnualLeaves wantedLeaves) {
        return wantedLeaves.isMatchNotEnoughCriteria(this.annualLeaveCriteria);
    }
}

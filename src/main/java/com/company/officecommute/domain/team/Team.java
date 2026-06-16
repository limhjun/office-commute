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

    public Team(Long teamId, String name, String managerName, int annualLeaveCriteria) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", name));
        }
        if (annualLeaveCriteria < 0) {
            throw new IllegalArgumentException("팀 연차 등록 기준은 0 이상이어야 합니다.");
        }
        this.teamId = teamId;
        this.name = name;
        this.managerName = managerName;
        this.annualLeaveCriteria = annualLeaveCriteria;
    }

    public static Team register(String name, String managerName, int annualLeaveCriteria) {
        String normalizedManager = (managerName == null || managerName.isBlank()) ? null : managerName.trim();
        return new Team(null, name, normalizedManager, annualLeaveCriteria);
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

    public int getAnnualLeaveCriteria() {
        return this.annualLeaveCriteria;
    }

    public boolean isNotEnoughCriteria(List<AnnualLeave> wantedLeaves) {
        return isNotEnoughCriteria(new AnnualLeaves(wantedLeaves));
    }

    public boolean isNotEnoughCriteria(AnnualLeaves wantedLeaves) {
        return wantedLeaves.isMatchNotEnoughCriteria(this.annualLeaveCriteria);
    }
}

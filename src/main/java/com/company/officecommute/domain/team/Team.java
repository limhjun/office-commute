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

    private int memberCount;

    private int annualLeaveCriteria;

    protected Team() {
    }

    public Team(String name) {
        this(null, name, null, 0);
    }

    public Team(String name, String managerName, int memberCount) {
        this(null, name, managerName, memberCount);
    }

    /**
     * 신규 팀 등록 흐름 전용 팩토리. 매니저는 옵셔널이며 빈 문자열은 null로 정규화한다.
     */
    public static Team register(String name, String managerName) {
        String normalizedManager = (managerName == null || managerName.isBlank()) ? null : managerName.trim();
        return new Team(null, name, normalizedManager, 0, 0);
    }

    public Team(Long teamId, String name, String managerName, int memberCount) {
        this(teamId, name, managerName, memberCount, 0);
    }

    public Team(Long teamId, String name, String managerName, int memberCount, int annualLeaveCriteria) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", name));
        }
        this.teamId = teamId;
        this.name = name;
        this.managerName = managerName;
        this.memberCount = memberCount;
        this.annualLeaveCriteria = annualLeaveCriteria;
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

    public int getMemberCount() {
        return this.memberCount;
    }

    public void increaseMemberCount() {
        this.memberCount++;
    }

    public void decreaseMemberCount() {
        if (memberCount > 0) {
            this.memberCount--;
        }
    }

    public boolean isNotEnoughCriteria(List<AnnualLeave> wantedLeaves) {
        return isNotEnoughCriteria(new AnnualLeaves(wantedLeaves));
    }

    public boolean isNotEnoughCriteria(AnnualLeaves wantedLeaves) {
        return wantedLeaves.isMatchNotEnoughCriteria(this.annualLeaveCriteria);
    }
}

package com.company.officecommute.domain.annual_leave;

import com.company.officecommute.domain.team.Team;

import java.util.ArrayList;
import java.util.List;

public class AnnualLeaveEnrollment {
    private final Long employeeId;
    private final Team team;
    private final List<AnnualLeave> existingLeaves;

    public AnnualLeaveEnrollment(Long employeeId, Team team) {
        this(employeeId, team, new ArrayList<>());
    }

    public AnnualLeaveEnrollment(Long employeeId, Team team, List<AnnualLeave> existingLeaves) {
        this.employeeId = employeeId;
        this.team = team;
        this.existingLeaves = existingLeaves;
    }

    public void enroll(AnnualLeaves wantedLeaves) {
        enroll(new AnnualLeaves(existingLeaves), wantedLeaves);
    }

    public void enroll(List<AnnualLeave> existingLeaves, List<AnnualLeave> wantedLeaves) {
        enroll(new AnnualLeaves(existingLeaves), new AnnualLeaves(wantedLeaves));
    }

    public void enroll(AnnualLeaves existingLeaves, AnnualLeaves wantedLeaves) {
        if (this.team.isNotEnoughCriteria(wantedLeaves)) {
            throw new AnnualLeaveCriteriaNotMetException();
        }
        existingLeaves.enroll(wantedLeaves.getAnnualLeaves());
    }
}

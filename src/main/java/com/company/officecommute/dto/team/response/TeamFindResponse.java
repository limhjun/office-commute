package com.company.officecommute.dto.team.response;

import com.company.officecommute.domain.team.Team;

public record TeamFindResponse(
        Long teamId,
        String name,
        String managerName,
        int annualLeaveCriteria,
        long memberCount
) {
    public static TeamFindResponse from(Team team, long memberCount) {
        return new TeamFindResponse(
                team.getTeamId(),
                team.getName(),
                team.getManagerName(),
                team.getAnnualLeaveCriteria(),
                memberCount
        );
    }
}

package com.company.officecommute.domain.team;

public class TeamNotFoundException extends RuntimeException {
    public TeamNotFoundException(Long teamId) {
        super(String.format("존재하지 않는 팀입니다: %d", teamId));
    }
}

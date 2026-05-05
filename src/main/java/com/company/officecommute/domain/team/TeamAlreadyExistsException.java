package com.company.officecommute.domain.team;

public class TeamAlreadyExistsException extends RuntimeException {
    public TeamAlreadyExistsException(String teamName) {
        super(String.format("이미 존재하는 팀입니다: %s", teamName));
    }
}

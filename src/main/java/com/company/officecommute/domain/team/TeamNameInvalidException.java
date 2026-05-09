package com.company.officecommute.domain.team;

public class TeamNameInvalidException extends RuntimeException {
    public TeamNameInvalidException(String name) {
        super(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", name));
    }
}

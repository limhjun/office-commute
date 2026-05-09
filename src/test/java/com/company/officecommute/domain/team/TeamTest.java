package com.company.officecommute.domain.team;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.service.team.Teams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamTest {

    @NullAndEmptySource
    @ParameterizedTest
    void testTeamNameException(String expected) {
        assertThatThrownBy(() -> Teams.createTeamWithTeamName(expected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", expected));
    }

    @Test
    void testAnnualLeaveCriteriaException() {
        assertThatThrownBy(() -> Teams.createTeamWithCriteria(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("팀 연차 등록 기준은 0 이상이어야 합니다.");
    }

    @Test
    void testIsNotEnoughCriteria_False() {
        Team team = Teams.createTeamWithCriteria(10);

        AnnualLeave enoughLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10));
        List<AnnualLeave> enoughLeaves = new ArrayList<>(List.of(enoughLeave));

        assertThat(team.isNotEnoughCriteria(enoughLeaves)).isFalse();
    }

    @Test
    void testIsNotEnoughCriteria_True() {
        Team team = Teams.createTeamWithCriteria(10);

        AnnualLeave notEnoughLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(9));
        List<AnnualLeave> notEnoughLeaves = new ArrayList<>(List.of(notEnoughLeave));

        assertThat(team.isNotEnoughCriteria(notEnoughLeaves)).isTrue();
    }
}

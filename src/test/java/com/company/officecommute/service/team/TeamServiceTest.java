package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamAlreadyExistsException;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.repository.team.TeamRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @InjectMocks
    private TeamService teamService;
    @Mock
    private TeamRepository teamRepository;

    @Test
    void testRegisterTeam() {
        TeamRegisterRequest request = new TeamRegisterRequest("ATeam", "이매니저");
        BDDMockito.given(teamRepository.findByName("ATeam"))
                .willReturn(Optional.empty());

        teamService.registerTeam(request);

        BDDMockito.verify(teamRepository).save(any(Team.class));
    }

    @Test
    void testRegisterTeamWithoutManager() {
        TeamRegisterRequest request = new TeamRegisterRequest("ATeam", null);
        BDDMockito.given(teamRepository.findByName("ATeam"))
                .willReturn(Optional.empty());

        teamService.registerTeam(request);

        BDDMockito.verify(teamRepository).save(any(Team.class));
    }

    @Test
    void testFindTeam() {
        Team team = new Team(1L, "ATeam", "이매니저", 0);
        BDDMockito.given(teamRepository.findAll())
                .willReturn(List.of(team));

        List<TeamFindResponse> teams = teamService.findTeam();

        assertThat(teams.size()).isEqualTo(1);
        assertThat(teams.get(0).teamId()).isEqualTo(1L);
        assertThat(teams.get(0).name()).isEqualTo("ATeam");
        assertThat(teams.get(0).managerName()).isEqualTo("이매니저");
    }

    @Test
    void testRegisterTeamException() {
        String teamName = "ATeam";
        TeamRegisterRequest request = new TeamRegisterRequest(teamName, null);

        BDDMockito.given(teamRepository.findByName(teamName))
                .willReturn(Optional.of(new Team(teamName)));

        Assertions.assertThatThrownBy(() -> teamService.registerTeam(request))
                .isInstanceOf(TeamAlreadyExistsException.class)
                .hasMessageContaining(teamName);
    }
}

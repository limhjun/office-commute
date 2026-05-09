package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamAlreadyExistsException;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.dto.team.response.TeamRegisterResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
    @Mock
    private EmployeeRepository employeeRepository;

    @Test
    void testRegisterTeam() {
        TeamRegisterRequest request = new TeamRegisterRequest("ATeam", "이매니저", 3);
        BDDMockito.given(teamRepository.findByName("ATeam"))
                .willReturn(Optional.empty());
        BDDMockito.given(teamRepository.save(any(Team.class)))
                .willReturn(new Team(1L, "ATeam", "이매니저", 3));

        TeamRegisterResponse response = teamService.registerTeam(request);

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        BDDMockito.verify(teamRepository).save(teamCaptor.capture());
        assertThat(response.teamId()).isEqualTo(1L);
        assertThat(teamCaptor.getValue().getAnnualLeaveCriteria()).isEqualTo(3);
    }

    @Test
    void testRegisterTeamWithoutManager() {
        TeamRegisterRequest request = new TeamRegisterRequest("ATeam", null, null);
        BDDMockito.given(teamRepository.findByName("ATeam"))
                .willReturn(Optional.empty());
        BDDMockito.given(teamRepository.save(any(Team.class)))
                .willReturn(new Team(1L, "ATeam", null, 0));

        TeamRegisterResponse response = teamService.registerTeam(request);

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        BDDMockito.verify(teamRepository).save(teamCaptor.capture());
        assertThat(response.teamId()).isEqualTo(1L);
        assertThat(teamCaptor.getValue().getAnnualLeaveCriteria()).isZero();
    }

    @Test
    void testFindTeam() {
        Team team = new Team(1L, "ATeam", "이매니저", 3);
        BDDMockito.given(teamRepository.findAll())
                .willReturn(List.of(team));
        BDDMockito.given(employeeRepository.countMembersByTeamIdsRaw(List.of(1L)))
                .willReturn(List.<Object[]>of(new Object[]{1L, 3L}));

        List<TeamFindResponse> teams = teamService.findTeam();

        assertThat(teams.size()).isEqualTo(1);
        assertThat(teams.get(0).teamId()).isEqualTo(1L);
        assertThat(teams.get(0).name()).isEqualTo("ATeam");
        assertThat(teams.get(0).managerName()).isEqualTo("이매니저");
        assertThat(teams.get(0).annualLeaveCriteria()).isEqualTo(3);
        assertThat(teams.get(0).memberCount()).isEqualTo(3L);
    }

    @Test
    void testFindTeamReturnsEmptyListShortCircuitsCountQuery() {
        BDDMockito.given(teamRepository.findAll()).willReturn(List.of());

        List<TeamFindResponse> teams = teamService.findTeam();

        assertThat(teams).isEmpty();
        BDDMockito.verify(employeeRepository, BDDMockito.never())
                .countMembersByTeamIdsRaw(BDDMockito.anyList());
    }

    @Test
    void testFindTeamMembersWithMissingTeamsCountedAsZero() {
        Team teamWithMembers = new Team(1L, "ATeam", null);
        Team emptyTeam = new Team(2L, "BTeam", null);
        BDDMockito.given(teamRepository.findAll())
                .willReturn(List.of(teamWithMembers, emptyTeam));
        BDDMockito.given(employeeRepository.countMembersByTeamIdsRaw(List.of(1L, 2L)))
                .willReturn(List.<Object[]>of(new Object[]{1L, 5L}));

        List<TeamFindResponse> teams = teamService.findTeam();

        assertThat(teams).extracting(TeamFindResponse::teamId, TeamFindResponse::memberCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 5L),
                        org.assertj.core.groups.Tuple.tuple(2L, 0L)
                );
    }

    @Test
    void testRegisterTeamException() {
        String teamName = "ATeam";
        TeamRegisterRequest request = new TeamRegisterRequest(teamName, null, null);

        BDDMockito.given(teamRepository.findByName(teamName))
                .willReturn(Optional.of(new Team(teamName)));

        Assertions.assertThatThrownBy(() -> teamService.registerTeam(request))
                .isInstanceOf(TeamAlreadyExistsException.class)
                .hasMessageContaining(teamName);
    }

    @Test
    void testRegisterTeamConvertsDataIntegrityViolationToDomainException() {
        String teamName = "ATeam";
        TeamRegisterRequest request = new TeamRegisterRequest(teamName, "이매니저", null);

        BDDMockito.given(teamRepository.findByName(teamName))
                .willReturn(Optional.empty());
        BDDMockito.given(teamRepository.save(any(Team.class)))
                .willThrow(new DataIntegrityViolationException("uk_team_name"));

        Assertions.assertThatThrownBy(() -> teamService.registerTeam(request))
                .isInstanceOf(TeamAlreadyExistsException.class)
                .hasMessageContaining(teamName);
    }
}

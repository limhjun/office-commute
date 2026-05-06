package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamAlreadyExistsException;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;

    public TeamService(TeamRepository teamRepository, EmployeeRepository employeeRepository) {
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void registerTeam(TeamRegisterRequest request) {
        String teamName = request.teamName();
        teamRepository.findByName(teamName).ifPresent(team -> {
            throw new TeamAlreadyExistsException(teamName);
        });
        try {
            teamRepository.save(Team.register(teamName, request.managerName()));
        } catch (DataIntegrityViolationException e) {
            throw new TeamAlreadyExistsException(teamName);
        }
    }

    @Transactional(readOnly = true)
    public List<TeamFindResponse> findTeam() {
        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = teams.stream().map(Team::getTeamId).toList();
        Map<Long, Long> countByTeamId = employeeRepository.countMembersByTeamIdsRaw(teamIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
        return teams.stream()
                .map(team -> TeamFindResponse.from(team, countByTeamId.getOrDefault(team.getTeamId(), 0L)))
                .toList();
    }
}

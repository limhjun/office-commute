package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamAlreadyExistsException;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
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
        return teamRepository.findAll().stream()
                .map(TeamFindResponse::from)
                .toList();
    }
}

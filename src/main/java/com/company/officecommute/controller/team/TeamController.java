package com.company.officecommute.controller.team;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.service.team.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @ManagerOnly
    @PostMapping("/team")
    public void registerTeam(@Valid @RequestBody TeamRegisterRequest request) {
        teamService.registerTeam(request);
    }

    @GetMapping("/team")
    public List<TeamFindResponse> findAllTeam() {
        return teamService.findTeam();
    }
}

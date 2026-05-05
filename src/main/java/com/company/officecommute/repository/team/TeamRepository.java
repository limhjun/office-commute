package com.company.officecommute.repository.team;

import com.company.officecommute.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByName(String teamName);
}

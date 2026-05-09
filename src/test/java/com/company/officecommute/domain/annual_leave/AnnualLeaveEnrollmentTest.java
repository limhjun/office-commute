package com.company.officecommute.domain.annual_leave;

import com.company.officecommute.service.team.Teams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AnnualLeaveEnrollmentTest {

    @Test
    void testEnroll() {
        AnnualLeaveEnrollment before = new AnnualLeaveEnrollment(1L, Teams.createTeamWithCriteria(10));

        AnnualLeave existingLeave = new AnnualLeave(1L, 1L, LocalDate.now());
        List<AnnualLeave> existingLeaves = new ArrayList<>(List.of(existingLeave));

        AnnualLeave wantedLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(20));
        List<AnnualLeave> wantedLeaves = new ArrayList<>(List.of(wantedLeave));

        before.enroll(existingLeaves, wantedLeaves);
    }

    @Test
    void testEnrollExceptionBecauseOfCriteria() {
        AnnualLeaveEnrollment before = new AnnualLeaveEnrollment(1L, Teams.createTeamWithCriteria(10));

        AnnualLeave existingLeave = new AnnualLeave(1L, 1L, LocalDate.now());
        List<AnnualLeave> existingLeaves = new ArrayList<>(List.of(existingLeave));

        AnnualLeave wantedLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(9));
        List<AnnualLeave> wantedLeaves = new ArrayList<>(List.of(wantedLeave));

        assertThatThrownBy(() -> before.enroll(existingLeaves, wantedLeaves))
                .isInstanceOf(AnnualLeaveCriteriaNotMetException.class);
    }
}

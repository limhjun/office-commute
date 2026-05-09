package com.company.officecommute.domain.annual_leave;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnualLeavesTest {

    @Test
    void testEnroll() {
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>());
        List<AnnualLeave> wantedLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));

        annualLeaves.enroll(new ArrayList<>(wantedLeaves));

        assertThat(annualLeaves.numberOfLeaves()).isEqualTo(1);
    }

    @Test
    void testEnrollExceptionWhenExist() {
        List<AnnualLeave> existingLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>(existingLeaves));
        List<AnnualLeave> wantedLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));

        assertThatThrownBy(() -> annualLeaves.enroll(new ArrayList<>(wantedLeaves)))
                .isInstanceOf(AnnualLeaveDuplicateException.class);
    }

    @Test
    void testEnrollExceptionWhenWantedLeavesContainDuplicate() {
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>());
        LocalDate wantedDate = LocalDate.now().plusDays(1);
        List<AnnualLeave> wantedLeaves = List.of(
                new AnnualLeave(1L, 1L, wantedDate),
                new AnnualLeave(2L, 1L, wantedDate)
        );

        assertThatThrownBy(() -> annualLeaves.enroll(new ArrayList<>(wantedLeaves)))
                .isInstanceOf(AnnualLeaveDuplicateException.class);
    }

    @Test
    void testIsMatchNotEnoughCriteria() {
        List<AnnualLeave> existingLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>(existingLeaves));
        int annualLeaveCriteria = 1;

        boolean result = annualLeaves.isMatchNotEnoughCriteria(annualLeaveCriteria);

        assertThat(result).isTrue();
    }
}

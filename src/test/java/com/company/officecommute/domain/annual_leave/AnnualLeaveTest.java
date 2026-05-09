package com.company.officecommute.domain.annual_leave;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnualLeaveTest {

    @Test
    void testInstantiate() {
        new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10));

        assertThatThrownBy(() -> new AnnualLeave(1L, 1L, LocalDate.now().minusDays(10)))
                .isInstanceOf(AnnualLeavePastDateException.class);
    }

    @Test
    void testIsNotEnoughForEnroll() {
        AnnualLeave annualLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(9));

        assertThat(annualLeave.isNotEnoughForEnroll(10)).isTrue();
        assertThat(annualLeave.isNotEnoughForEnroll(9)).isFalse();
    }

    @Test
    void testIsRemain() {
        AnnualLeave annualLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10));

        assertThat(annualLeave.isRemain()).isTrue();


        annualLeave = new AnnualLeave(1L, 1L, LocalDate.now());

        assertThat(annualLeave.isRemain()).isFalse();
    }
}

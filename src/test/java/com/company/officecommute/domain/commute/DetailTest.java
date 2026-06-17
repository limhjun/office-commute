package com.company.officecommute.domain.commute;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DetailTest {

    @Test
    void equalsAndHashCode() {
        Detail detail = new Detail(LocalDate.of(2026, 6, 17), 480L, false);
        Detail sameDetail = new Detail(LocalDate.of(2026, 6, 17), 480L, false);

        assertThat(detail).isEqualTo(sameDetail);
        assertThat(detail).hasSameHashCodeAs(sameDetail);
    }
}

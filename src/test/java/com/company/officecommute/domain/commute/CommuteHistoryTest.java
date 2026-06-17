package com.company.officecommute.domain.commute;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CommuteHistoryTest {

    private static final String KOREA = "Asia/Seoul";

    @Test
    void testEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        CommuteHistory commuteHistoryAfterEndWork = commuteHistory.endWork(workEndTime);

        assertThat(commuteHistoryAfterEndWork.getWorkingMinutes()).isEqualTo(10L * 60);
    }

    @Test
    void testEndWorkWhenNotStartWork() {
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));

        CommuteHistory commuteHistory = CommuteHistoryFixture.notStarted(1L, 1L);
        assertThatThrownBy(() -> commuteHistory.endWork(workEndTime))
                .isInstanceOf(CommuteNotStartedException.class)
                .hasMessage("진행 중인 출근 기록이 없습니다.");
    }

    @Test
    void testEndWorkWhenAlreadyEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));

        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);
        assertThatThrownBy(() -> commuteHistory.endWork(workEndTime))
                .isInstanceOf(CommuteAlreadyEndedException.class)
                .hasMessage("이미 퇴근 처리된 근무입니다.");
    }

    @Test
    void calculateWorkingMinutes_computesWithoutMutatingState() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        long workingMinutes = commuteHistory.calculateWorkingMinutes(workEndTime);

        assertThat(workingMinutes).isEqualTo(10L * 60);
        // 조건부 update가 유일한 쓰기 경로가 되도록 엔티티 상태는 그대로여야 한다
        assertThat(commuteHistory.endTimeIsNull()).isTrue();
        assertThat(commuteHistory.getWorkingMinutes()).isZero();
    }

    @Test
    void calculateWorkingMinutes_throwsWhenAlreadyEnded() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        assertThatThrownBy(() -> commuteHistory.calculateWorkingMinutes(workEndTime))
                .isInstanceOf(CommuteAlreadyEndedException.class)
                .hasMessage("이미 퇴근 처리된 근무입니다.");
    }

    @Test
    void testEndTimeIsNull() {
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, ZonedDateTime.now());

        assertThat(commuteHistory.endTimeIsNull()).isTrue();
    }

    @Test
    void testEndWorkBeforeStartThrows() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime earlierThanStart = ZonedDateTime.of(2024, 1, 1, 7, 30, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThatThrownBy(() -> commuteHistory.endWork(earlierThanStart))
                .isInstanceOf(InvalidCommuteRangeException.class)
                .hasMessage("퇴근 시간이 출근 시간보다 이릅니다.");
    }

    @Test
    void toDetail_workingDate() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        Detail detail = commuteHistory.toDetail();

        assertThat(detail.getDate()).isEqualTo(workStartTime.toLocalDate());
        assertThat(detail.getWorkingMinutes()).isEqualTo(10L * 60);
        assertThat(detail.isUsingDayOff()).isFalse();
    }

    @Test
    void toDetail_usesWorkDateCalculatedByWorkZone() {
        ZoneId utc = ZoneId.of("UTC");
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 15, 30, 0, 0, utc);
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 8, 1, 1, 0, 0, 0, korea);
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(
                1L, 1L, workStartTime, workEndTime, korea);

        Detail detail = commuteHistory.toDetail();

        assertThat(commuteHistory.getWorkDate()).isEqualTo(LocalDate.of(2024, 8, 1));
        assertThat(workStartTime.toLocalDate()).isEqualTo(LocalDate.of(2024, 7, 31));
        assertThat(detail.getDate()).isEqualTo(LocalDate.of(2024, 8, 1));
    }

    @Test
    void toDetail_AnnualLeaveDate() {
        LocalDate annualLeaveDate = LocalDate.of(2024, 1, 1);
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(1L, annualLeaveDate, ZoneId.of(KOREA));

        Detail detail = commuteHistory.toDetail();

        assertThat(detail.getDate()).isEqualTo(annualLeaveDate);
        assertThat(detail.getWorkingMinutes()).isEqualTo(0);
        assertThat(detail.isUsingDayOff()).isTrue();
    }
}

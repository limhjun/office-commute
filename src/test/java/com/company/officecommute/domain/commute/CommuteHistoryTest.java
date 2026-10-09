package com.company.officecommute.domain.commute;

import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

        CommuteHistory commuteHistoryAfterEndWork = commuteHistory.endWork(workEndTime.toInstant());

        assertThat(commuteHistoryAfterEndWork.getWorkingMinutes()).isEqualTo(10L * 60);
    }

    @Test
    void testRegisterWorkStartWithoutStartTimeThrows() {
        assertThatThrownBy(() -> CommuteHistory.registerWorkStart(1L, null, ZoneId.of(KOREA)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workStartTime은 null일 수 없습니다");
    }

    @Test
    void testEndWorkWhenAlreadyEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));

        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);
        assertThatThrownBy(() -> commuteHistory.endWork(workEndTime.toInstant()))
                .isInstanceOf(CommuteAlreadyEndedException.class)
                .hasMessage("이미 퇴근 처리된 근무입니다.");
    }

    @Test
    void calculateWorkingMinutes_computesWithoutMutatingState() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        long workingMinutes = commuteHistory.calculateWorkingMinutes(workEndTime.toInstant());

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

        assertThatThrownBy(() -> commuteHistory.calculateWorkingMinutes(workEndTime.toInstant()))
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

        assertThatThrownBy(() -> commuteHistory.endWork(earlierThanStart.toInstant()))
                .isInstanceOf(InvalidCommuteRangeException.class)
                .hasMessage("퇴근 시간이 출근 시간보다 이릅니다.");
    }

    @Test
    void toDailyWorkDuration_workingDate() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        DailyWorkDuration dailyWorkDuration = commuteHistory.toDailyWorkDuration();

        assertThat(dailyWorkDuration.getDate()).isEqualTo(workStartTime.toLocalDate());
        assertThat(dailyWorkDuration.getWorkingMinutes()).isEqualTo(10L * 60);
        assertThat(dailyWorkDuration.isUsingDayOff()).isFalse();
    }

    @Test
    void toDailyWorkDuration_usesWorkDateCalculatedByWorkZone() {
        ZoneId utc = ZoneId.of("UTC");
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 15, 30, 0, 0, utc);
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 8, 1, 1, 0, 0, 0, korea);
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(
                1L, 1L, workStartTime, workEndTime, korea);

        DailyWorkDuration dailyWorkDuration = commuteHistory.toDailyWorkDuration();

        assertThat(commuteHistory.getWorkDate()).isEqualTo(LocalDate.of(2024, 8, 1));
        assertThat(workStartTime.toLocalDate()).isEqualTo(LocalDate.of(2024, 7, 31));
        assertThat(dailyWorkDuration.getDate()).isEqualTo(LocalDate.of(2024, 8, 1));
    }

    @Test
    void status_isCompletedOnceWorkEnded() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        assertThat(commuteHistory.status(workEndTime.plusDays(3).toInstant(), workStartTime.plusDays(2).toInstant()))
                .isEqualTo(CommuteStatus.COMPLETED);
    }

    @Test
    void status_isDayOffForAnnualLeave() {
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(
                1L, LocalDate.of(2024, 1, 1), ZoneId.of(KOREA));

        assertThat(commuteHistory.status(Instant.parse("2024-01-01T05:00:00Z"), null))
                .isEqualTo(CommuteStatus.DAY_OFF);
    }

    @Test
    @DisplayName("날짜가 바뀐 야간근무도 최신 근무이고 24시간 이내면 IN_PROGRESS 다")
    void status_overnightWithin24HoursIsInProgress() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 22, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.status(workStartTime.plusHours(10).toInstant(), workStartTime.toInstant()))
                .isEqualTo(CommuteStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("정확히 24시간까지는 IN_PROGRESS, 1초라도 넘으면 CORRECTION_REQUIRED")
    void status_24HourBoundary() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);
        Instant latest = workStartTime.toInstant();

        assertThat(commuteHistory.status(workStartTime.plusHours(24).toInstant(), latest))
                .isEqualTo(CommuteStatus.IN_PROGRESS);
        assertThat(commuteHistory.status(workStartTime.plusHours(24).plusSeconds(1).toInstant(), latest))
                .isEqualTo(CommuteStatus.CORRECTION_REQUIRED);
    }

    @Test
    @DisplayName("후속 실제 근무가 있으면 24시간 이내라도 CORRECTION_REQUIRED")
    void status_laterWorkMakesCorrectionRequired() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 20, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);
        Instant nextDayStart = workStartTime.plusHours(13).toInstant();

        assertThat(commuteHistory.status(workStartTime.plusHours(14).toInstant(), nextDayStart))
                .isEqualTo(CommuteStatus.CORRECTION_REQUIRED);
    }

    @Test
    void status_judgesByElapsedTimeNotByTheServerOrCallerZone() {
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 2, 9, 0, 0, 0, losAngeles);
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime, losAngeles);

        // UTC 로 읽으면 이미 1월 3일이지만, 상태는 경과 시간과 후속 근무로만 판정한다.
        Instant sameEvening = ZonedDateTime.of(2024, 1, 2, 18, 0, 0, 0, losAngeles).toInstant();
        assertThat(sameEvening.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 3));

        assertThat(commuteHistory.status(sameEvening, workStartTime.toInstant()))
                .isEqualTo(CommuteStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("일반 퇴근은 정확히 24시간까지 허용하고 초과하면 거부한다")
    void calculateRegularEndMinutes_24HourBoundary() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.calculateRegularEndMinutes(workStartTime.plusHours(24).toInstant()))
                .isEqualTo(24L * 60);
        assertThatThrownBy(() -> commuteHistory.calculateRegularEndMinutes(
                workStartTime.plusHours(24).plusSeconds(1).toInstant()))
                .isInstanceOf(CommuteEndWindowExpiredException.class);
    }

    @Test
    @DisplayName("일반 퇴근 — 이미 종료된 기록은 시간 창보다 먼저 AlreadyEnded")
    void calculateRegularEndMinutes_alreadyEnded() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(
                1L, 1L, workStartTime, workStartTime.plusHours(9));

        assertThatThrownBy(() -> commuteHistory.calculateRegularEndMinutes(workStartTime.plusDays(3).toInstant()))
                .isInstanceOf(CommuteAlreadyEndedException.class);
    }

    @Test
    @DisplayName("정정 근무 분 — 출근과 같은 시각 0분, 59초 0분, 60초 1분 (분 미만 절삭)")
    void calculateCorrectedWorkingMinutes_truncatesBelowMinute() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);
        Instant now = workStartTime.plusDays(5).toInstant();

        assertThat(commuteHistory.calculateCorrectedWorkingMinutes(workStartTime.toInstant(), now, null)).isZero();
        assertThat(commuteHistory.calculateCorrectedWorkingMinutes(
                workStartTime.plusSeconds(59).toInstant(), now, null)).isZero();
        assertThat(commuteHistory.calculateCorrectedWorkingMinutes(
                workStartTime.plusSeconds(60).toInstant(), now, null)).isEqualTo(1);
    }

    @Test
    @DisplayName("정정은 24시간 제한이 없다 — 26시간 근무도 승인으로 기록할 수 있다")
    void calculateCorrectedWorkingMinutes_allowsOver24Hours() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.calculateCorrectedWorkingMinutes(
                workStartTime.plusHours(26).toInstant(), workStartTime.plusDays(5).toInstant(), null))
                .isEqualTo(26L * 60);
    }

    @Test
    @DisplayName("정정 시각 검증 — 출근 이전·미래·후속 근무 이후·변경 없음·연차를 거부한다")
    void calculateCorrectedWorkingMinutes_rejectsInvalidTimes() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory open = CommuteHistoryFixture.open(1L, 1L, workStartTime);
        CommuteHistory ended = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workStartTime.plusHours(9));
        Instant now = workStartTime.plusHours(30).toInstant();
        Instant nextStart = workStartTime.plusHours(25).toInstant();

        assertCorrectionError(() -> open.calculateCorrectedWorkingMinutes(
                workStartTime.minusSeconds(1).toInstant(), now, null), CorrectionErrorCode.CORRECTION_END_BEFORE_START);
        assertCorrectionError(() -> open.calculateCorrectedWorkingMinutes(
                now.plusSeconds(1), now, null), CorrectionErrorCode.CORRECTION_END_IN_FUTURE);
        assertCorrectionError(() -> open.calculateCorrectedWorkingMinutes(
                nextStart.plusSeconds(1), now, nextStart), CorrectionErrorCode.CORRECTION_OVERLAPS_NEXT_WORK);
        assertCorrectionError(() -> ended.calculateCorrectedWorkingMinutes(
                workStartTime.plusHours(9).toInstant(), now, null), CorrectionErrorCode.CORRECTION_NO_CHANGE);
        CommuteHistory dayOff = CommuteHistoryFixture.annualLeave(1L, LocalDate.of(2024, 1, 1), ZoneId.of(KOREA));
        assertCorrectionError(() -> dayOff.calculateCorrectedWorkingMinutes(
                workStartTime.plusHours(9).toInstant(), now, null), CorrectionErrorCode.CORRECTION_TARGET_DAY_OFF);

        // 후속 근무의 출근 시각과 같은 종료는 허용한다
        assertThat(open.calculateCorrectedWorkingMinutes(nextStart, now, nextStart)).isEqualTo(25L * 60);
        // 완료 기록은 앞당기거나 늦출 수 있다
        assertThat(ended.calculateCorrectedWorkingMinutes(workStartTime.plusHours(8).toInstant(), now, null))
                .isEqualTo(8L * 60);
        assertThat(ended.calculateCorrectedWorkingMinutes(workStartTime.plusHours(11).toInstant(), now, null))
                .isEqualTo(11L * 60);
    }

    private static void assertCorrectionError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                              CorrectionErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(CorrectionException.class)
                .extracting(e -> ((CorrectionException) e).getCode())
                .isEqualTo(code);
    }

    @Test
    void zonedTimes_areRenderedInWorkZoneOffset() {
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 22, 30, 0, 0, korea);
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 8, 1, 6, 0, 0, 0, korea);
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(
                1L, 1L, workStartTime, workEndTime, korea);

        assertThat(commuteHistory.zonedWorkStartTime()).isEqualTo(workStartTime.toOffsetDateTime());
        // 자정을 넘긴 근무 — 퇴근 시각의 날짜가 workDate 와 다르다는 사실이 응답에 남는다
        assertThat(commuteHistory.zonedWorkEndTime()).isEqualTo(workEndTime.toOffsetDateTime());
        assertThat(commuteHistory.getWorkDate()).isEqualTo(LocalDate.of(2024, 7, 31));
    }

    @Test
    void zonedTimes_useWorkZoneNotTheZoneTheInstantWasCreatedIn() {
        ZoneId utc = ZoneId.of("UTC");
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 23, 0, 0, 0, utc);
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime, korea);

        assertThat(commuteHistory.zonedWorkStartTime())
                .isEqualTo(ZonedDateTime.of(2024, 8, 1, 8, 0, 0, 0, korea).toOffsetDateTime());
    }

    @Test
    void zonedWorkEndTime_isNullWhileCommuteIsOpen() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.zonedWorkEndTime()).isNull();
    }

    @Test
    void zonedTimes_areNullForAnnualLeave() {
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(
                1L, LocalDate.of(2024, 1, 1), ZoneId.of(KOREA));

        assertThat(commuteHistory.zonedWorkStartTime()).isNull();
        assertThat(commuteHistory.zonedWorkEndTime()).isNull();
    }

    @Test
    void toDailyWorkDuration_AnnualLeaveDate() {
        LocalDate annualLeaveDate = LocalDate.of(2024, 1, 1);
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(1L, annualLeaveDate, ZoneId.of(KOREA));

        DailyWorkDuration dailyWorkDuration = commuteHistory.toDailyWorkDuration();

        assertThat(dailyWorkDuration.getDate()).isEqualTo(annualLeaveDate);
        assertThat(dailyWorkDuration.getWorkingMinutes()).isEqualTo(0);
        assertThat(dailyWorkDuration.isUsingDayOff()).isTrue();
    }
}

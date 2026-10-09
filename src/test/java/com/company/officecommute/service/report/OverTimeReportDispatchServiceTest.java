package com.company.officecommute.service.report;

import com.company.officecommute.domain.closing.ClosingErrorCode;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.closing.MonthlyClosing;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.domain.report.DispatchFailureReason;
import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportFinality;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.dto.report.request.DispatchConfirmationOutcome;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.mail.ReportMailer;
import com.company.officecommute.repository.closing.MonthlyClosingRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.repository.report.ReportFileRepository;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeReportSnapshot;
import com.company.officecommute.service.overtime.UnclosedCommute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OverTimeReportDispatchServiceTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");

    @Mock
    private ReportDispatchRepository reportDispatchRepository;

    @Mock
    private OverTimeReportService overTimeReportService;

    @Mock
    private ReportMailer reportMailer;

    @Mock
    private ReportFileRepository reportFileRepository;

    @Mock
    private MonthlyClosingRepository monthlyClosingRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private OverTimeReportDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new OverTimeReportDispatchService(
                reportDispatchRepository,
                reportFileRepository,
                monthlyClosingRepository,
                employeeRepository,
                overTimeReportService,
                reportMailer,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        // 기본 전제: 7월은 신규 기능으로 수동 마감됐다. 마감 전 보류는 별도 테스트가 다룬다.
        lenient().when(monthlyClosingRepository.findByTargetYearMonth(JULY))
                .thenReturn(Optional.of(closing(MonthlyClosingType.MANUAL)));
        lenient().when(reportFileRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 빈 파일은 보관을 거부하므로 엑셀 작성 목도 내용을 쓴다
        try {
            lenient().doAnswer(invocation -> {
                invocation.<java.io.OutputStream>getArgument(1).write("xlsx".getBytes());
                return null;
            }).when(overTimeReportService).writeExcelReport(any(), any());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("월 마감 전이면 집계·메일 없이 FAILED(MONTH_NOT_CLOSED)로 보류하고 관리자 알림도 보내지 않는다")
    void monthNotClosed_holdsWithoutMail() {
        given(monthlyClosingRepository.findByTargetYearMonth(JULY)).willReturn(Optional.empty());
        givenNoPriorDispatch();

        dispatchService.dispatch(JULY);

        then(overTimeReportService).shouldHaveNoInteractions();
        then(reportMailer).shouldHaveNoInteractions();
        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason()).startsWith(DispatchFailureReason.MONTH_NOT_CLOSED.name());
    }

    @Test
    @DisplayName("최초 시도는 최종 파일을 보관한 뒤 그 파일로 발송한다")
    void firstAttempt_storesFinalFileBeforeSending() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));

        dispatchService.dispatch(JULY);

        ArgumentCaptor<ReportFile> stored = ArgumentCaptor.forClass(ReportFile.class);
        then(reportFileRepository).should().saveAndFlush(stored.capture());
        assertThat(stored.getValue().getFileName()).isEqualTo("2026년7월_초과근무보고서.xlsx");
        assertThat(stored.getValue().getEmployeeCount()).isEqualTo(2);
        then(reportMailer).should().sendMonthlyReport(stored.getValue());
    }

    @Test
    @DisplayName("재시도는 보관 파일을 그대로 보낸다 — 직원·공휴일이 바뀌어도 다시 집계하지 않는다")
    void retry_usesStoredFileWithoutRegenerating() {
        ReportDispatch failed = ReportDispatch.claim(JULY, NOW.minusSeconds(60));
        failed.markFailed("MAIL_SEND_FAILED: smtp down", NOW.minusSeconds(30));
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL))
                .willReturn(Optional.of(failed));
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        ReportFile stored = storedFile(ReportKind.ORIGINAL);
        given(reportFileRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL))
                .willReturn(Optional.of(stored));

        dispatchService.dispatch(JULY);

        then(overTimeReportService).shouldHaveNoInteractions();
        then(reportMailer).should().sendMonthlyReport(stored);
        assertThat(capturedSave().isSent()).isTrue();
    }

    @Test
    @DisplayName("파일 보관에 실패하면 발송하지 않고 FAILED 로 남는다")
    void fileStoreFailure_doesNotSend() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));
        given(reportFileRepository.saveAndFlush(any()))
                .willThrow(new DataAccessResourceFailureException("disk full"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should(never()).sendMonthlyReport(any());
        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason()).startsWith(DispatchFailureReason.UNEXPECTED.name());
    }

    @Test
    @DisplayName("기존 발송 월을 정정 후 마감했다면 원본 이력과 별개인 정정본(CORRECTION)으로 보낸다")
    void legacyCorrectedMonth_sendsCorrectionSeparately() {
        given(monthlyClosingRepository.findByTargetYearMonth(JULY))
                .willReturn(Optional.of(closing(MonthlyClosingType.LEGACY_CORRECTED)));
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.CORRECTION))
                .willReturn(Optional.empty());
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.CORRECTION)).willReturn(snapshot(2));

        dispatchService.dispatch(JULY);

        ArgumentCaptor<ReportFile> stored = ArgumentCaptor.forClass(ReportFile.class);
        then(reportFileRepository).should().saveAndFlush(stored.capture());
        assertThat(stored.getValue().getKind()).isEqualTo(ReportKind.CORRECTION);
        assertThat(stored.getValue().getFileName()).isEqualTo("2026년7월_초과근무보고서_정정본.xlsx");
        then(reportMailer).should().sendCorrectedMonthlyReport(stored.getValue());
        then(reportMailer).should(never()).sendMonthlyReport(any());
        assertThat(capturedSave().getKind()).isEqualTo(ReportKind.CORRECTION);
    }

    @Test
    @DisplayName("수신 불명 확인 — DELIVERED 는 SENT, NOT_DELIVERED 는 FAILED 로 재시도를 연다")
    void confirmDelivery_transitions() {
        ReportDispatch delivered = ReportDispatch.claim(JULY, NOW);
        delivered.commitDelivery(NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL))
                .willReturn(Optional.of(delivered));
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        dispatchService.confirmDelivery(1L, JULY, ReportKind.ORIGINAL, DispatchConfirmationOutcome.DELIVERED, "대표 수신 확인");

        assertThat(delivered.isSent()).isTrue();
        assertThat(delivered.getDeliveryConfirmedById()).isEqualTo(1L);

        ReportDispatch notDelivered = ReportDispatch.claim(JULY, NOW);
        notDelivered.commitDelivery(NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL))
                .willReturn(Optional.of(notDelivered));

        dispatchService.confirmDelivery(1L, JULY, ReportKind.ORIGINAL, DispatchConfirmationOutcome.NOT_DELIVERED, "미수신");

        assertThat(notDelivered.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(notDelivered.isDeliveryFinalized()).isFalse();
    }

    @Test
    @DisplayName("수신 불명이 아닌 발송은 확인할 수 없다")
    void confirmDelivery_rejectsWhenNotUncertain() {
        ReportDispatch sent = ReportDispatch.claim(JULY, NOW);
        sent.markSent(NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL))
                .willReturn(Optional.of(sent));

        assertThatThrownBy(() -> dispatchService.confirmDelivery(
                1L, JULY, ReportKind.ORIGINAL, DispatchConfirmationOutcome.DELIVERED, "확인"))
                .isInstanceOf(ClosingException.class)
                .extracting(e -> ((ClosingException) e).getCode())
                .isEqualTo(ClosingErrorCode.DISPATCH_NOT_UNCERTAIN);
    }

    @Test
    @DisplayName("이미 발송된 달은 메일러를 한 번도 부르지 않는다 — 멱등의 본체")
    void alreadySent_doesNothing() {
        ReportDispatch sent = ReportDispatch.claim(JULY, NOW);
        sent.markSent(NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(sent));

        dispatchService.dispatch(JULY);

        then(reportMailer).shouldHaveNoInteractions();
        then(overTimeReportService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 실행이 리스를 잡고 있으면 중복으로 집계하지 않는다")
    void leaseHeldByAnotherRun_doesNothing() {
        ReportDispatch inProgress = ReportDispatch.claim(JULY, NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(inProgress));

        dispatchService.dispatch(JULY);

        then(reportMailer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("선점 조회 DB 오류도 스케줄러 스레드로 던지지 않는다")
    void claimPersistenceFailure_isSwallowed() {
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL))
                .willThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(overTimeReportService).shouldHaveNoInteractions();
        then(reportMailer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("리스가 만료된 IN_PROGRESS는 회수해 다시 시도한다 — 그 달이 영원히 잠기면 안 된다")
    void expiredLease_isReclaimed() {
        ReportDispatch stale = ReportDispatch.claim(JULY, NOW.minusSeconds(3600));
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(stale));
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));

        dispatchService.dispatch(JULY);

        then(reportMailer).should().sendMonthlyReport(any());
    }

    @Test
    @DisplayName("FAILED 재선점 낙관적 락에서 진 실행은 발송 단계로 넘어가지 않는다")
    void concurrentRetryLoser_doesNotSend() {
        ReportDispatch failed = ReportDispatch.claim(JULY, NOW.minusSeconds(60));
        failed.markFailed("temporary failure", NOW.minusSeconds(30));
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(failed));
        given(reportDispatchRepository.saveAndFlush(failed))
                .willThrow(new OptimisticLockingFailureException("claim lost"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(overTimeReportService).shouldHaveNoInteractions();
        then(reportMailer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미마감이 있으면 대표에게 가지 않고 관리자 경고만 나가며 FAILED(UNCLOSED_COMMUTES)로 남는다")
    void unclosedCommutes_gatesCeoMail() {
        givenNoPriorDispatch();
        List<UnclosedCommute> unclosed = List.of(
                new UnclosedCommute("EMP001", "임형준", LocalDate.of(2026, 7, 31))
        );
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL))
                .willReturn(new OverTimeReportSnapshot(report(2, unclosed.size()), unclosed));

        dispatchService.dispatch(JULY);

        then(reportMailer).should(never()).sendMonthlyReport(any());
        then(reportMailer).should().sendUnclosedCommuteWarning(any(), any(), eq(unclosed));

        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.UNCLOSED_COMMUTES.name());
    }

    @Test
    @DisplayName("공휴일 API 이용 불가는 대표 미발송 + FAILED로 남고, 예외를 밖으로 던지지 않는다")
    void holidayDataUnavailable_recordedNotThrown() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL))
                .willThrow(new HolidayDataUnavailableException("resultCode=22"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should(never()).sendMonthlyReport(any());
        then(reportMailer).should().sendDispatchFailure(
                JULY,
                DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE,
                "resultCode=22"
        );
        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("SMTP 오류는 MAIL_SEND_FAILED로 분류되고 스케줄러 스레드를 죽이지 않는다")
    void mailFailure_classifiedAndSwallowed() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));
        willThrow(new MailSendException("smtp down")).given(reportMailer).sendMonthlyReport(any());

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.MAIL_SEND_FAILED.name());
        then(reportMailer).should().sendDispatchFailure(
                JULY,
                DispatchFailureReason.MAIL_SEND_FAILED,
                "smtp down"
        );
    }

    @Test
    @DisplayName("실패 알림 SMTP 오류도 원래 실패 이력을 유지하고 스케줄러 스레드로 던지지 않는다")
    void failureNotificationFailure_isSwallowedAfterRecording() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL))
                .willThrow(new HolidayDataUnavailableException("resultCode=22"));
        willThrow(new MailSendException("smtp still down")).given(reportMailer)
                .sendDispatchFailure(JULY, DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE, "resultCode=22");

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("실패 이력 저장 DB 오류도 알림을 시도하고 스케줄러 스레드로 던지지 않는다")
    void failurePersistenceFailure_isSwallowedWithoutSuppressingNotification() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL))
                .willThrow(new HolidayDataUnavailableException("resultCode=22"));
        given(reportDispatchRepository.save(any()))
                .willThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should().sendDispatchFailure(
                JULY,
                DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE,
                "resultCode=22"
        );
    }

    @Test
    @DisplayName("성공하면 SENT + sentAt이 남고, 이어지는 두 번째 발송은 아무 일도 하지 않는다")
    void success_thenSecondDispatchIsNoop() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));

        dispatchService.dispatch(JULY);

        ReportDispatch sent = capturedSave();
        assertThat(sent.isSent()).isTrue();
        assertThat(sent.getSentAt()).isEqualTo(NOW);

        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(sent));
        dispatchService.dispatch(JULY);

        then(reportMailer).should(times(1)).sendMonthlyReport(any());
    }

    @Test
    @DisplayName("최종 점검에서 이미 발송된 달은 조용히 넘어간다")
    void alertIfNotSent_silentWhenSent() {
        ReportDispatch sent = ReportDispatch.claim(JULY, NOW);
        sent.markSent(NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(sent));

        dispatchService.alertIfNotSent(JULY);

        then(reportMailer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("최종 점검에서 미발송이면 기록된 사유 분류로 관리자에게 알린다")
    void alertIfNotSent_reportsRecordedReason() {
        ReportDispatch failed = ReportDispatch.claim(JULY, NOW);
        failed.markFailed(DispatchFailureReason.UNCLOSED_COMMUTES.name() + ": 미마감 3건", NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(failed));

        dispatchService.alertIfNotSent(JULY);

        then(reportMailer).should().sendDispatchFailure(
                eq(JULY), eq(DispatchFailureReason.UNCLOSED_COMMUTES), any());
    }

    @Test
    @DisplayName("이력 자체가 없으면 스케줄러 미동작으로 보고 알린다 — 가장 조용한 실패다")
    void alertIfNotSent_noHistoryAtAll() {
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.empty());

        dispatchService.alertIfNotSent(JULY);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        then(reportMailer).should().sendDispatchFailure(
                eq(JULY), eq(DispatchFailureReason.UNEXPECTED), detail.capture());
        assertThat(detail.getValue()).contains("스케줄러");
    }

    @Test
    @DisplayName("SENT 기록은 발송 확정 flush가 돌려준 엔티티로 한다 — merge는 version을 반환 객체에만 반영한다")
    void recordSent_usesEntityReturnedByCommitFlush() {
        // 트랜잭션 없는 detached 저장에서 반환 엔티티를 버리면 로컬 version이 낡아
        // 이후의 모든 save가 낙관적 락 충돌로 실패한다(SENT가 영원히 기록되지 않는다).
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.empty());
        ReportDispatch merged = ReportDispatch.claim(JULY, NOW);
        merged.commitDelivery(NOW);
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> {
            ReportDispatch saved = invocation.getArgument(0);
            if (saved.getStatus() == DispatchStatus.DELIVERY_COMMITTED && saved != merged) {
                return merged; // DB가 version을 올려 돌려준 merge 결과를 흉내 낸다
            }
            return saved;
        });
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));

        dispatchService.dispatch(JULY);

        ReportDispatch recorded = capturedSave();
        assertThat(recorded).isSameAs(merged);
        assertThat(recorded.isSent()).isTrue();
    }

    @Test
    @DisplayName("발송 확정 flush의 낙관적 락 충돌은 정상 경합이다 — 실패 기록도 실패 알림도 내지 않는다")
    void commitDeliveryLockLoser_stepsAsideQuietly() {
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.empty());
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> {
            ReportDispatch saved = invocation.getArgument(0);
            if (saved.getStatus() == DispatchStatus.DELIVERY_COMMITTED) {
                throw new OptimisticLockingFailureException("다른 실행이 리스를 회수했다");
            }
            return saved;
        });
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should(never()).sendMonthlyReport(any());
        then(reportMailer).should(never()).sendDispatchFailure(any(), any(), any());
        then(reportDispatchRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("미마감 보류 이력 저장이 실패해도 사유가 UNEXPECTED로 덮이거나 시도가 이중 집계되지 않는다")
    void unclosedRecordPersistenceFailure_doesNotDoubleMark() {
        givenNoPriorDispatch();
        List<UnclosedCommute> unclosed = List.of(
                new UnclosedCommute("EMP001", "임형준", LocalDate.of(2026, 7, 31))
        );
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL))
                .willReturn(new OverTimeReportSnapshot(report(2, unclosed.size()), unclosed));
        given(reportDispatchRepository.save(any()))
                .willThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should(never()).sendDispatchFailure(any(), any(), any());
        ReportDispatch recorded = capturedSave(); // save는 정확히 한 번만 시도된다
        assertThat(recorded.getAttemptCount()).isEqualTo(1);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.UNCLOSED_COMMUTES.name());
    }

    @Test
    @DisplayName("CEO 메일 성공 후 SENT 저장이 실패해도 FAILED로 분류하거나 재발송하지 않는다")
    void sentPersistenceFailure_keepsDeliveryCommittedAndPreventsDuplicate() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReportSnapshot(JULY, ReportFinality.FINAL)).willReturn(snapshot(2));
        AtomicBoolean deliveryCommitFlushed = new AtomicBoolean();
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> {
            ReportDispatch saved = invocation.getArgument(0);
            if (saved.getStatus() == DispatchStatus.DELIVERY_COMMITTED) {
                deliveryCommitFlushed.set(true);
            }
            return saved;
        });
        given(reportDispatchRepository.save(any()))
                .willThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should().sendMonthlyReport(any());
        then(reportMailer).should(never()).sendDispatchFailure(any(), any(), any());
        assertThat(deliveryCommitFlushed).isTrue();

        ReportDispatch committed = ReportDispatch.claim(JULY, NOW);
        committed.commitDelivery(NOW);
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.of(committed));

        dispatchService.dispatch(JULY);

        then(reportMailer).should(times(1)).sendMonthlyReport(any());
        assertThat(committed.getStatus()).isEqualTo(DispatchStatus.DELIVERY_COMMITTED);
    }

    private void givenNoPriorDispatch() {
        given(reportDispatchRepository.findByTargetYearMonthAndKind(JULY, ReportKind.ORIGINAL)).willReturn(Optional.empty());
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    /** 결과 기록은 markSent/markFailed 뒤의 save 로 남는다. */
    private ReportDispatch capturedSave() {
        ArgumentCaptor<ReportDispatch> captor = ArgumentCaptor.forClass(ReportDispatch.class);
        then(reportDispatchRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private OverTimeReport report(int rowCount, long unclosedCount) {
        List<OverTimeReportData> rows = java.util.stream.IntStream.range(0, rowCount)
                .mapToObj(i -> new OverTimeReportData("EMP00" + i, "직원" + i, "백엔드팀", 60, 0, 0, 22500))
                .toList();
        return new OverTimeReport(JULY, rows, unclosedCount);
    }

    private static MonthlyClosing closing(MonthlyClosingType type) {
        return new MonthlyClosing(JULY, type, LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 31), 1L, NOW, null);
    }

    private static ReportFile storedFile(ReportKind kind) {
        return new ReportFile(JULY, kind, "2026년7월_초과근무보고서.xlsx", "xlsx".getBytes(), 2, NOW.minusSeconds(60));
    }

    private OverTimeReportSnapshot snapshot(int rowCount) {
        return new OverTimeReportSnapshot(report(rowCount, 0), List.of());
    }
}

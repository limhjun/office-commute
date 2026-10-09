package com.company.officecommute.service.report;

import com.company.officecommute.domain.closing.ClosingErrorCode;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.closing.MonthlyClosing;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.domain.report.DispatchFailureReason;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportFinality;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.dto.employee.response.EmployeeRef;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.report.request.DispatchConfirmationOutcome;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.mail.ReportMailException;
import com.company.officecommute.mail.ReportMailer;
import com.company.officecommute.repository.closing.MonthlyClosingRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.repository.report.ReportFileRepository;
import com.company.officecommute.service.overtime.OverTimeReportFileName;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeReportSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 초과근무 리포트 발송의 <b>유일한 진입점</b>. 매월 배치도, 관리자의 수동 재실행도, 월 마감 직후 발송도 이것을 부른다.
 * <p>
 * 모든 진입점이 같은 순서를 거친다: 선점 → 월 마감 확인 → (최초 시도만) 최종 Excel 생성·보관 → 보관 파일 발송.
 * 발송 종류(원본·정정본)는 마감 정보가 정한다. 재시도와 확정본 다운로드는 보관 파일을 그대로 쓴다.
 * <p>
 * 멱등성의 근거는 {@code report_dispatch}의 {@code UNIQUE(target_year_month, kind)}와 종착 상태다.
 * {@code DELIVERY_COMMITTED} 또는 {@code SENT}인 달은 몇 번을 불러도 아무 일도 일어나지 않으므로
 * 강제 발송 플래그를 두지 않는다.
 * <p>
 * 발송 진입점({@link #dispatch})은 예외를 밖으로 던지지 않는다. 스케줄러 스레드에서 예외가 올라가면 그 뒤의
 * 재시도까지 함께 사라진다 — 모든 실패는 이력에 기록되고 분류된다.
 */
@Service
public class OverTimeReportDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OverTimeReportDispatchService.class);

    /**
     * 선점 리스. 발송 도중 프로세스가 죽으면 {@code IN_PROGRESS}가 남는데, 회수 없이는
     * 그 달이 영원히 잠긴다. 한 번의 발송(집계 + 엑셀 + SMTP)이 넉넉히 끝나는 시간으로 잡는다.
     */
    private static final Duration LEASE = Duration.ofMinutes(30);

    private final ReportDispatchRepository reportDispatchRepository;
    private final ReportFileRepository reportFileRepository;
    private final MonthlyClosingRepository monthlyClosingRepository;
    private final EmployeeRepository employeeRepository;
    private final OverTimeReportService overTimeReportService;
    private final ReportMailer reportMailer;
    private final Clock clock;

    public OverTimeReportDispatchService(
            ReportDispatchRepository reportDispatchRepository,
            ReportFileRepository reportFileRepository,
            MonthlyClosingRepository monthlyClosingRepository,
            EmployeeRepository employeeRepository,
            OverTimeReportService overTimeReportService,
            ReportMailer reportMailer,
            Clock clock
    ) {
        this.reportDispatchRepository = reportDispatchRepository;
        this.reportFileRepository = reportFileRepository;
        this.monthlyClosingRepository = monthlyClosingRepository;
        this.employeeRepository = employeeRepository;
        this.overTimeReportService = overTimeReportService;
        this.reportMailer = reportMailer;
        this.clock = clock;
    }

    public void dispatch(YearMonth target) {
        try {
            dispatchClaimedReport(target, resolveKind(target));
        } catch (Exception e) {
            // claim 자체나 실패 처리용 DB 저장도 실패할 수 있다. 스케줄러 진입점의 최종 경계에서
            // 모두 흡수해야 다음 예약 실행이 사라지지 않는다.
            log.error("초과근무 리포트 발송 진입점에서 오류를 처리하지 못했다 — 대상 월 {}", target, e);
        }
    }

    /**
     * 기능 도입 전 발송 월을 정정 후 마감했다면 정정본, 그 외에는 원본이다. 기존 발송 월을 정정 없이
     * 등록한 달은 원본이 이미 SENT 라 아무 일도 일어나지 않는다.
     */
    private ReportKind resolveKind(YearMonth target) {
        return monthlyClosingRepository.findByTargetYearMonth(target)
                .filter(closing -> closing.getClosingType() == MonthlyClosingType.LEGACY_CORRECTED)
                .map(closing -> ReportKind.CORRECTION)
                .orElse(ReportKind.ORIGINAL);
    }

    private void dispatchClaimedReport(YearMonth target, ReportKind kind) {
        ReportDispatch dispatch = claim(target, kind);
        if (dispatch == null) {
            return;
        }

        log.info("초과근무 리포트 발송 시도 시작 — 대상 월 {} ({}), 시도 {}회차", target, kind, dispatch.getAttemptCount() + 1);
        try {
            runDispatch(target, kind, dispatch);
        } catch (OptimisticLockingFailureException e) {
            // 재선점 경합에서 진 것 — 이 달은 이긴 실행의 소유다. 장애가 아니므로 실패로 기록하지 않는다.
            log.info("발송 준비 중 다른 실행이 이 달을 가져갔다 — 대상 월 {}", target);
        } catch (HolidayDataUnavailableException e) {
            // 공휴일 fail-closed. 사람이 할 조치가 없고 재시도가 흡수한다.
            recordFailureAndNotify(dispatch, DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE, e.getMessage());
        } catch (ReportMailException | MailException e) {
            recordFailureAndNotify(dispatch, DispatchFailureReason.MAIL_SEND_FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("초과근무 리포트 발송 중 예상하지 못한 오류 — 대상 월 {}", target, e);
            recordFailureAndNotify(dispatch, DispatchFailureReason.UNEXPECTED, e.toString());
        }
    }

    /**
     * 수동 재실행. 배치와 <b>완전히 같은</b> {@link #dispatch}를 부르고 현재 상태를 돌려준다.
     * 강제 발송 플래그는 두지 않는다 — 이미 보낸 달을 한 번 더 보낸다는 요구가 아직 없고,
     * 생긴다면 그때 별도 유스케이스로 설계한다.
     */
    public OverTimeReportDispatchResponse dispatchAndDescribe(YearMonth target) {
        dispatch(target);
        ReportKind kind = resolveKind(target);
        return reportDispatchRepository.findByTargetYearMonthAndKind(target, kind)
                .map(this::describe)
                .orElseThrow(() -> new IllegalStateException(
                        "발송을 시도했는데 이력이 없다 — 선점 경로가 깨졌다: " + target + " " + kind));
    }

    public List<OverTimeReportDispatchResponse> describeAll(YearMonth target) {
        return reportDispatchRepository.findAllByTargetYearMonthOrderByKindAsc(target).stream()
                .map(this::describe)
                .toList();
    }

    private OverTimeReportDispatchResponse describe(ReportDispatch dispatch) {
        boolean fileAvailable = reportFileRepository.existsByTargetYearMonthAndKind(
                dispatch.getTargetYearMonth(), dispatch.getKind());
        EmployeeRef confirmedBy = Optional.ofNullable(dispatch.getDeliveryConfirmedById())
                .flatMap(employeeRepository::findById)
                .map(EmployeeRef::from)
                .orElse(null);
        return OverTimeReportDispatchResponse.from(dispatch, fileAvailable, confirmedBy);
    }

    /**
     * 수신 여부 불명(DELIVERY_COMMITTED)의 운영자 확인. SMTP 와 DB 사이의 exactly-once 를 보장할 수 없으므로
     * 사람이 확인한 사실을 기록하는 경로다. 미수신이면 FAILED 로 돌려 보관 파일로 재발송할 수 있게 한다.
     */
    @Transactional
    public OverTimeReportDispatchResponse confirmDelivery(
            Long confirmedById,
            YearMonth target,
            ReportKind kind,
            DispatchConfirmationOutcome outcome,
            String note
    ) {
        ReportDispatch dispatch = reportDispatchRepository.findByTargetYearMonthAndKind(target, kind)
                .orElseThrow(() -> new ClosingException(ClosingErrorCode.DISPATCH_NOT_FOUND));
        if (!dispatch.isDeliveryUncertain()) {
            throw new ClosingException(ClosingErrorCode.DISPATCH_NOT_UNCERTAIN);
        }
        Instant now = clock.instant();
        switch (outcome) {
            case DELIVERED -> dispatch.confirmDelivered(confirmedById, note, now);
            case NOT_DELIVERED -> dispatch.confirmNotDelivered(confirmedById, note, now);
        }
        log.warn("초과근무 리포트 수신 여부 운영자 확인 — 대상 월 {} ({}), 결과 {}, 확인자 {}",
                target, kind, outcome, confirmedById);
        return describe(reportDispatchRepository.saveAndFlush(dispatch));
    }

    @Transactional(readOnly = true)
    public ReportFile findFinalFile(YearMonth target, ReportKind kind) {
        return reportFileRepository.findByTargetYearMonthAndKind(target, kind)
                .orElseThrow(() -> new ClosingException(ClosingErrorCode.REPORT_FILE_NOT_FOUND));
    }

    /**
     * 재시도 창을 다 쓰고도 발송되지 않은 달을 관리자에게 드러낸다.
     * "메일이 안 온 것"과 "발송이 실패한 것"이 구분되지 않으면 그것이 조용한 실패다.
     * <p>
     * 시도 로직에 "이번이 마지막인가" 조건을 섞지 않기 위해 별도 진입점으로 둔다.
     */
    public void alertIfNotSent(YearMonth target) {
        Optional<ReportDispatch> found = reportDispatchRepository.findByTargetYearMonthAndKind(target, resolveKind(target));
        if (found.isPresent() && found.get().isSent()) {
            return;
        }

        String detail = found
                .map(d -> "%d회 시도, 마지막 사유: %s".formatted(d.getAttemptCount(), d.getLastFailureReason()))
                .orElse("발송이 한 번도 시도되지 않았습니다 — 스케줄러 미동작을 의심해야 합니다.");
        log.error("초과근무 리포트 최종 미발송 — 대상 월 {} ({})", target, detail);

        try {
            reportMailer.sendDispatchFailure(target, found.map(this::recordedReason).orElse(DispatchFailureReason.UNEXPECTED), detail);
        } catch (ReportMailException | MailException e) {
            // 알림 경로 자체가 죽은 경우다. 메일로 알릴 방법이 없으니 로그가 마지막 방어선이다.
            log.error("최종 미발송 알림 메일마저 실패했다 — 대상 월 {}", target, e);
        }
    }

    /** {@code markFailed}가 남긴 "REASON: 상세" 문자열에서 분류를 되읽는다. */
    private DispatchFailureReason recordedReason(ReportDispatch dispatch) {
        String recorded = dispatch.getLastFailureReason();
        if (recorded == null) {
            return DispatchFailureReason.UNEXPECTED;
        }
        for (DispatchFailureReason reason : DispatchFailureReason.values()) {
            if (recorded.startsWith(reason.name())) {
                return reason;
            }
        }
        return DispatchFailureReason.UNEXPECTED;
    }

    /**
     * 이 달을 이 실행이 가져갈 수 있으면 선점한 이력을, 아니면 {@code null}을 준다.
     * <p>
     * 최초 선점 경합은 유니크 제약이, 재시도/리스 회수 경합은 엔티티의 낙관적 락 버전이
     * 판정한다 — 충돌한 실행은 {@code save}가 실패하므로 발송 단계로 넘어가지 못한다.
     */
    private ReportDispatch claim(YearMonth target, ReportKind kind) {
        Instant now = clock.instant();
        Optional<ReportDispatch> found = reportDispatchRepository.findByTargetYearMonthAndKind(target, kind);

        if (found.isEmpty()) {
            try {
                return reportDispatchRepository.saveAndFlush(ReportDispatch.claim(target, kind, now));
            } catch (DataIntegrityViolationException e) {
                log.info("다른 실행이 이미 선점했다 — 대상 월 {}", target);
                return null;
            }
        }

        ReportDispatch dispatch = found.get();
        if (dispatch.isDeliveryFinalized()) {
            if (!dispatch.isSent()) {
                // DELIVERY_COMMITTED 후 SENT 기록 전에 중단된 달. 실제 수신 여부를 알 수 없으므로
                // 자동 재발송하지 않는다 — 사람이 확인하고 조치해야 하는 상태다.
                log.warn("발송 확정 후 결과가 기록되지 않은 달 — 대상 월 {}, 마지막 시도 {}."
                                + " 대표 수신 여부를 수동으로 확인해야 한다",
                        target, dispatch.getLastAttemptedAt());
            } else {
                log.info("이미 발송된 달이라 아무것도 하지 않는다 — 대상 월 {}, 발송 시각 {}",
                        target, dispatch.getSentAt());
            }
            return null;
        }
        if (dispatch.isLeaseHeld(now, LEASE)) {
            log.info("다른 실행이 진행 중이다 — 대상 월 {}, 마지막 시도 {}", target, dispatch.getLastAttemptedAt());
            return null;
        }

        dispatch.beginAttempt(now);
        // 낙관적 락 충돌을 발송 전에 확정해야 한다. save만 두면 상위 트랜잭션이 생겼을 때
        // 실제 UPDATE/버전 검사가 SMTP 호출 뒤로 미뤄질 수 있다.
        try {
            return reportDispatchRepository.saveAndFlush(dispatch);
        } catch (OptimisticLockingFailureException e) {
            // 최초 선점의 유니크 위반과 같은 결이다 — 정상 경합이므로 INFO로만 남긴다.
            log.info("다른 실행이 먼저 재선점했다 — 대상 월 {}", target);
            return null;
        }
    }

    /**
     * 집계·엑셀·발송은 전부 DB 트랜잭션 <b>밖</b>이다. {@code generateReport}가 공휴일 API를
     * 라이브 호출하므로, 트랜잭션 안에 두면 외부 API 응답 시간만큼 커넥션을 붙잡는다.
     */
    private void runDispatch(YearMonth target, ReportKind kind, ReportDispatch dispatch) throws Exception {
        Optional<MonthlyClosing> closing = monthlyClosingRepository.findByTargetYearMonth(target);
        if (closing.isEmpty()) {
            // 장애가 아니라 보류다. 매 예약 시도마다 관리자에게 메일을 보내지 않고 이력만 남긴다 —
            // 끝내 발송되지 않으면 alertIfNotSent 가 알린다.
            recordFailure(dispatch, DispatchFailureReason.MONTH_NOT_CLOSED,
                    "월 마감 전 — 근태 관리자가 월 마감하면 그 직후 발송된다");
            return;
        }

        Optional<ReportFile> stored = reportFileRepository.findByTargetYearMonthAndKind(target, kind);
        ReportFile file;
        if (stored.isPresent()) {
            // 재시도: 직원·팀·공휴일이 그 사이 바뀌었어도 처음 보관한 파일을 그대로 보낸다.
            file = stored.get();
        } else {
            file = generateAndStore(target, kind, dispatch);
            if (file == null) {
                return;
            }
        }

        // SMTP 성공과 DB 저장은 하나의 트랜잭션으로 묶을 수 없다. 먼저 발송 결정을
        // flush해야 SMTP 성공 후 SENT 저장이 실패해도 다음 실행이 중복 메일을 보내지 않는다.
        dispatch.commitDelivery(clock.instant());
        // JPA merge는 증가한 version을 반환 엔티티에만 반영한다. 이 참조를 버리면
        // 이후의 모든 저장이 낡은 version으로 낙관적 락 충돌을 일으킨다.
        dispatch = reportDispatchRepository.saveAndFlush(dispatch);

        try {
            switch (kind) {
                case ORIGINAL -> reportMailer.sendMonthlyReport(file);
                case CORRECTION -> reportMailer.sendCorrectedMonthlyReport(file);
            }
        } catch (ReportMailException | MailException e) {
            // 메일러가 예외를 던졌으면 미발송이므로 FAILED로 되돌려 재시도를 살린다.
            // 발송 결정 이후의 실패는 여기서 처리해야 갱신된 version의 엔티티로 기록된다.
            recordFailureAndNotify(dispatch, DispatchFailureReason.MAIL_SEND_FAILED, e.getMessage());
            return;
        } catch (Exception e) {
            // 발송 여부를 알 수 없는 실패. DELIVERY_COMMITTED를 유지해 자동 재발송을 막고 사람에게 알린다.
            log.error("초과근무 리포트 발송 결과 불명 — 대상 월 {}, DELIVERY_COMMITTED 유지", target, e);
            notifyDispatchFailure(target, DispatchFailureReason.UNEXPECTED, e.toString());
            return;
        }
        recordSent(dispatch, target, file.getEmployeeCount());
    }

    /**
     * 최종 파일을 만들어 <b>발송 전에</b> 보관한다. 보관에 실패하면 예외가 올라가 발송하지 않는다(UNEXPECTED).
     * 마감된 월만 여기 오므로 미퇴근이 있을 수 없지만, 있다면 기존처럼 발송을 막는다.
     */
    private ReportFile generateAndStore(YearMonth target, ReportKind kind, ReportDispatch dispatch) throws Exception {
        ReportFinality finality = ReportFinality.of(kind);
        OverTimeReportSnapshot snapshot = overTimeReportService.generateReportSnapshot(target, finality);
        OverTimeReport report = snapshot.report();
        byte[] excel = writeExcel(report);

        if (report.hasUnclosedCommutes()) {
            // 미마감은 그 직원의 초과근무를 과소 집계한다 = 조용히 틀린 급여 근거.
            // 엑셀 첫 행 경고만으로는 "대표가 경고를 읽었을 것"에 기대게 되므로 발송 자체를 막는다.
            reportMailer.sendUnclosedCommuteWarning(report, excel, snapshot.unclosedCommutes());
            try {
                recordFailure(
                        dispatch,
                        DispatchFailureReason.UNCLOSED_COMMUTES,
                        "미마감 %d건 — 마감되면 다음 재시도에서 자동 발송된다".formatted(report.unclosedCommuteCount())
                );
            } catch (Exception e) {
                // 바깥 catch로 보내면 markFailed가 같은 엔티티에 한 번 더 적용돼
                // 시도 횟수가 이중 집계되고 사유가 UNEXPECTED로 덮인다.
                log.error("미마감 보류 이력 저장 실패 — 대상 월 {}", target, e);
            }
            return null;
        }

        ReportFile file = new ReportFile(
                target, kind, OverTimeReportFileName.of(target, finality), excel, report.rows().size(), clock.instant());
        ReportFile saved = reportFileRepository.saveAndFlush(file);
        log.info("초과근무 리포트 최종 파일 보관 — 대상 월 {} ({}), {} bytes, sha256 {}",
                target, kind, saved.getSizeBytes(), saved.getSha256());
        return saved;
    }

    private void recordSent(ReportDispatch dispatch, YearMonth target, int recipientCount) {
        dispatch.markSent(clock.instant());
        try {
            reportDispatchRepository.save(dispatch);
        } catch (Exception e) {
            // DB에는 DELIVERY_COMMITTED가 남아 자동 재시도를 막는다. 이미 성공한 SMTP 발송을
            // FAILED로 덮으면 다음 실행이 대표에게 같은 메일을 다시 보낸다.
            log.error("초과근무 리포트 메일은 발송됐지만 SENT 상태 저장에 실패했다"
                            + " — 대상 월 {}, DELIVERY_COMMITTED로 재발송 차단",
                    target, e);
            return;
        }
        log.info("초과근무 리포트 발송 완료 — 대상 월 {}, 대상자 {}명", target, recipientCount);
    }

    /**
     * 엑셀을 <b>다 만든 뒤</b> 첨부한다. 쓰는 도중 실패하면 메일은 시작조차 하지 않으므로
     * 절반짜리 파일이 대표에게 갈 수 없다.
     */
    private byte[] writeExcel(OverTimeReport report) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        overTimeReportService.writeExcelReport(report, buffer);
        return buffer.toByteArray();
    }

    private void recordFailure(ReportDispatch dispatch, DispatchFailureReason reason, String detail) {
        log.warn("초과근무 리포트 발송 실패 — 대상 월 {}, 사유 {}, 상세 {}",
                dispatch.getTargetYearMonth(), reason, detail);
        dispatch.markFailed(reason.name() + ": " + detail, clock.instant());
        reportDispatchRepository.save(dispatch);
    }

    private void recordFailureAndNotify(ReportDispatch dispatch, DispatchFailureReason reason, String detail) {
        try {
            recordFailure(dispatch, reason, detail);
        } catch (Exception e) {
            // 실패를 기록하는 DB가 고장 난 경우에도 알림은 독립적으로 시도하고, 최종적으로는
            // dispatch의 바깥 경계가 스케줄러 스레드까지 예외가 전파되는 것을 막는다.
            log.error("초과근무 리포트 실패 이력 저장 불가 — 대상 월 {}, 사유 {}",
                    dispatch.getTargetYearMonth(), reason, e);
        }
        notifyDispatchFailure(dispatch.getTargetYearMonth(), reason, detail);
    }

    private void notifyDispatchFailure(YearMonth target, DispatchFailureReason reason, String detail) {
        try {
            reportMailer.sendDispatchFailure(target, reason, detail);
        } catch (ReportMailException | MailException e) {
            // 실패 알림도 같은 SMTP를 사용하므로 원래 메일 장애 때 함께 실패할 수 있다.
            // 원래 실패 이력은 유지하고 스케줄러의 다음 재시도를 막지 않는다.
            log.error("초과근무 리포트 실패 알림 발송 불가 — 대상 월 {}, 원래 사유 {}", target, reason, e);
        }
    }
}

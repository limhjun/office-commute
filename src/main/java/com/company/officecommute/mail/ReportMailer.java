package com.company.officecommute.mail;

import com.company.officecommute.domain.report.DispatchFailureReason;
import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportFinality;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.service.overtime.OverTimeReportFileName;
import com.company.officecommute.service.overtime.UnclosedCommute;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * 초과근무 리포트 관련 메일 발송.
 * <p>
 * 용도별로 메서드를 나눈다 — 수신자·제목·본문이 전부 다르므로 플래그 인자로 분기하면
 * "대표에게 갈 메일이 관리자에게 가는" 종류의 실수가 한 줄 조건문 뒤로 숨는다.
 */
@Component
public class ReportMailer {

    private static final String SUBJECT_PREFIX = "[초과근무] ";
    private static final String CALCULATION_BASIS = "1일 8시간·1주 40시간 초과, 휴일근로 별도 트랙";

    /** 본문이 무한정 길어지는 것을 막는다. 전체 목록은 첨부 리포트와 화면에서 확인한다. */
    private static final int MAX_LISTED_UNCLOSED = 50;

    private final JavaMailSender mailSender;
    private final ReportMailProperties properties;

    public ReportMailer(JavaMailSender mailSender, ReportMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * 확정본 — 대표에게. 월 마감 뒤 보관한 파일을 그대로 첨부한다(재시도도 같은 파일).
     */
    public void sendMonthlyReport(ReportFile file) {
        String body = """
                %s 초과근무 보고서(월 마감 확정본)를 첨부합니다.

                %s
                """.formatted(displayMonth(file.getTargetYearMonth()), finalSummary(file));

        send(
                List.of(requireConfigured(properties.getCeo(), "report.mail.ceo")),
                SUBJECT_PREFIX + displayMonth(file.getTargetYearMonth()) + " 초과근무 보고서",
                body,
                file.getFileName(),
                file.getContent()
        );
    }

    /**
     * 정정본 — 대표에게. 기능 도입 전에 이미 발송한 월을 정정 후 마감해 보낸다.
     * 기존 보고서 대신 이 파일을 쓰도록 제목·본문에서 분명히 안내한다.
     */
    public void sendCorrectedMonthlyReport(ReportFile file) {
        String body = """
                %s 초과근무 보고서의 정정본을 첨부합니다.
                앞서 받으신 %s 보고서 대신 이 정정본을 사용해 주십시오.

                %s
                """.formatted(
                displayMonth(file.getTargetYearMonth()),
                displayMonth(file.getTargetYearMonth()),
                finalSummary(file)
        );

        send(
                List.of(requireConfigured(properties.getCeo(), "report.mail.ceo")),
                SUBJECT_PREFIX + displayMonth(file.getTargetYearMonth()) + " 초과근무 보고서 (정정본)",
                body,
                file.getFileName(),
                file.getContent()
        );
    }

    /**
     * 퇴근 미마감으로 대표 발송을 보류했음 — 근태 관리자에게.
     * 리포트를 함께 첨부해 관리자가 수치를 바로 확인하고 교정할 수 있게 한다.
     */
    public void sendUnclosedCommuteWarning(OverTimeReport report, byte[] excel, List<UnclosedCommute> unclosed) {
        String body = """
                %s 초과근무 리포트의 대표 발송을 보류했습니다.

                %s

                %s

                %s
                """.formatted(
                displayMonth(report.yearMonth()),
                DispatchFailureReason.UNCLOSED_COMMUTES.getManagerGuidance(),
                summary(report),
                unclosedList(unclosed)
        );

        send(
                managers(),
                SUBJECT_PREFIX + displayMonth(report.yearMonth()) + " 리포트 발송 보류 — 퇴근 미마감 "
                        + report.unclosedCommuteCount() + "건",
                body,
                OverTimeReportFileName.of(report.yearMonth(), ReportFinality.REFERENCE),
                excel
        );
    }

    /**
     * 발송 실패 — 근태 관리자에게. "메일이 안 온 것"과 "발송이 실패한 것"이 구분돼야 한다.
     */
    public void sendDispatchFailure(YearMonth target, DispatchFailureReason reason, String detail) {
        String body = """
                %s 초과근무 리포트 발송이 실패했습니다.

                사유: %s
                %s

                상세: %s
                """.formatted(displayMonth(target), reason.name(), reason.getManagerGuidance(), detail);

        send(
                managers(),
                SUBJECT_PREFIX + displayMonth(target) + " 리포트 발송 실패 — " + reason.name(),
                body,
                null,
                null
        );
    }

    private void send(List<String> recipients, String subject, String body, String attachmentName, byte[] excel) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(requireConfigured(properties.getFrom(), "report.mail.from"));
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(body, false);
            if (excel != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(excel));
            }
        } catch (jakarta.mail.MessagingException e) {
            throw new ReportMailException("메일 구성에 실패했습니다: " + subject, e);
        }
        mailSender.send(message);
    }

    private List<String> managers() {
        List<String> managers = properties.getManagers();
        if (managers.isEmpty()) {
            // 관리자 주소가 비면 "조용한 실패"를 드러낼 통로 자체가 없어진다 — 설정 누락으로 다룬다.
            throw new ReportMailException("report.mail.managers 가 비어 있어 관리자 알림을 보낼 수 없습니다.");
        }
        return managers;
    }

    private String requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new ReportMailException(propertyName + " 가 설정되지 않았습니다.");
        }
        return value;
    }

    /**
     * 수신자가 첨부를 열지 않고도 신뢰성을 판단할 수 있어야 한다.
     */
    private String summary(OverTimeReport report) {
        return """
                - 대상 월: %s
                - 대상자: %d명
                - 퇴근 미마감: %d건
                - 산정 기준: %s""".formatted(
                displayMonth(report.yearMonth()),
                report.rows().size(),
                report.unclosedCommuteCount(),
                CALCULATION_BASIS
        );
    }

    private String finalSummary(ReportFile file) {
        return """
                - 대상 월: %s
                - 대상자: %d명
                - 상태: 월 마감 완료(미퇴근·승인 대기 정정 0건)
                - 산정 기준: %s""".formatted(
                displayMonth(file.getTargetYearMonth()),
                file.getEmployeeCount(),
                CALCULATION_BASIS
        );
    }

    private String unclosedList(List<UnclosedCommute> unclosed) {
        StringBuilder builder = new StringBuilder("미마감 목록 (")
                .append(unclosed.size())
                .append("건)");
        unclosed.stream()
                .limit(MAX_LISTED_UNCLOSED)
                .forEach(record -> builder.append("\n- ")
                        .append(record.employeeCode()).append(' ')
                        .append(record.employeeName()).append(' ')
                        .append(record.workDate()));
        if (unclosed.size() > MAX_LISTED_UNCLOSED) {
            builder.append("\n- ... 외 ").append(unclosed.size() - MAX_LISTED_UNCLOSED).append("건");
        }
        return builder.toString();
    }

    private String displayMonth(YearMonth yearMonth) {
        return yearMonth.getYear() + "년 " + yearMonth.getMonthValue() + "월";
    }
}

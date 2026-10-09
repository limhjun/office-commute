package com.company.officecommute.mail;

import com.company.officecommute.domain.report.DispatchFailureReason;
import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.service.overtime.UnclosedCommute;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReportMailerTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Mock
    private JavaMailSender mailSender;

    private ReportMailProperties properties;
    private ReportMailer mailer;

    @BeforeEach
    void setUp() {
        properties = new ReportMailProperties();
        properties.setFrom("noreply@company.com");
        properties.setCeo("ceo@company.com");
        properties.setManagers(List.of("hr1@company.com", "hr2@company.com"));
        mailer = new ReportMailer(mailSender, properties);
    }

    @Test
    @DisplayName("확정본은 대표에게만 가고, 보관 파일명 그대로 첨부되며 제목에 대상 월이 들어간다")
    void sendMonthlyReport_toCeoWithStoredFile() throws Exception {
        givenRealMimeMessage();

        mailer.sendMonthlyReport(storedFile(ReportKind.ORIGINAL, "2026년7월_초과근무보고서.xlsx"));

        MimeMessage sent = captureSent();
        assertThat(recipients(sent)).containsExactly("ceo@company.com");
        assertThat(sent.getSubject()).contains("2026년 7월").doesNotContain("정정본");
        assertThat(attachmentFileName(sent)).isEqualTo("2026년7월_초과근무보고서.xlsx");
    }

    @Test
    @DisplayName("확정본 본문에 대상자 수·월 마감 상태·산정 기준이 들어간다")
    void sendMonthlyReport_bodyCarriesReliabilitySignals() throws Exception {
        givenRealMimeMessage();

        mailer.sendMonthlyReport(storedFile(ReportKind.ORIGINAL, "2026년7월_초과근무보고서.xlsx"));

        String body = bodyText(captureSent());
        assertThat(body)
                .contains("대상 월: 2026년 7월")
                .contains("대상자: 2명")
                .contains("월 마감 완료")
                .contains("1일 8시간·1주 40시간 초과, 휴일근로 별도 트랙");
    }

    @Test
    @DisplayName("정정본은 제목·본문에서 기존 보고서 대신 사용하도록 안내한다")
    void sendCorrectedMonthlyReport_marksCorrection() throws Exception {
        givenRealMimeMessage();

        mailer.sendCorrectedMonthlyReport(storedFile(ReportKind.CORRECTION, "2026년7월_초과근무보고서_정정본.xlsx"));

        MimeMessage sent = captureSent();
        assertThat(recipients(sent)).containsExactly("ceo@company.com");
        assertThat(sent.getSubject()).contains("정정본");
        assertThat(bodyText(sent)).contains("대신 이 정정본을 사용");
        assertThat(attachmentFileName(sent)).isEqualTo("2026년7월_초과근무보고서_정정본.xlsx");
    }

    @Test
    @DisplayName("미마감 보류 경고는 관리자 전원에게 가고 본문에 미마감 목록이 들어간다")
    void sendUnclosedCommuteWarning_toManagersWithList() throws Exception {
        givenRealMimeMessage();

        mailer.sendUnclosedCommuteWarning(
                report(JULY, 2, 1),
                "xlsx-bytes".getBytes(),
                List.of(new UnclosedCommute("EMP001", "임형준", LocalDate.of(2026, 7, 31)))
        );

        MimeMessage sent = captureSent();
        assertThat(recipients(sent)).containsExactly("hr1@company.com", "hr2@company.com");
        assertThat(sent.getSubject()).contains("2026년 7월").contains("발송 보류");
        assertThat(bodyText(sent)).contains("EMP001 임형준 2026-07-31");
        // 관리자가 수치를 바로 확인할 수 있도록 리포트도 함께 간다
        assertThat(attachmentFileName(sent)).isEqualTo("2026년7월_초과근무보고서_참고용.xlsx");
    }

    @Test
    @DisplayName("발송 실패 알림은 관리자에게 사유 분류와 조치 안내를 담아 간다")
    void sendDispatchFailure_toManagersWithReason() throws Exception {
        givenRealMimeMessage();

        mailer.sendDispatchFailure(JULY, DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE, "resultCode=22");

        MimeMessage sent = captureSent();
        assertThat(recipients(sent)).containsExactly("hr1@company.com", "hr2@company.com");
        assertThat(sent.getSubject()).contains("2026년 7월").contains("HOLIDAY_DATA_UNAVAILABLE");
        assertThat(bodyText(sent))
                .contains(DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE.getManagerGuidance())
                .contains("resultCode=22");
    }

    @Test
    @DisplayName("관리자 주소가 비어 있으면 조용히 넘어가지 않고 설정 누락으로 실패한다")
    void managersNotConfigured_fails() {
        properties.setManagers(List.of());

        assertThatThrownBy(() -> mailer.sendDispatchFailure(JULY, DispatchFailureReason.UNEXPECTED, "boom"))
                .isInstanceOf(ReportMailException.class)
                .hasMessageContaining("report.mail.managers");

        then(mailSender).should(never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    private void givenRealMimeMessage() {
        // MimeMessageHelper 가 실제로 조립한 결과를 검증해야 하므로 목이 아닌 진짜 MimeMessage 를 준다
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((jakarta.mail.Session) null));
    }

    private MimeMessage captureSent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        then(mailSender).should().send(captor.capture());
        return captor.getValue();
    }

    private List<String> recipients(MimeMessage message) throws Exception {
        return List.of(message.getRecipients(Message.RecipientType.TO)).stream()
                .map(Object::toString)
                .toList();
    }

    private String attachmentFileName(MimeMessage message) throws Exception {
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            String fileName = multipart.getBodyPart(i).getFileName();
            if (fileName != null) {
                return fileName;
            }
        }
        return null;
    }

    private String bodyText(MimeMessage message) throws Exception {
        return readText(message.getContent());
    }

    private String readText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        MimeMultipart multipart = (MimeMultipart) content;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            if (multipart.getBodyPart(i).getFileName() == null) {
                builder.append(readText(multipart.getBodyPart(i).getContent()));
            }
        }
        return builder.toString();
    }

    private ReportFile storedFile(ReportKind kind, String fileName) {
        return new ReportFile(JULY, kind, fileName, "xlsx-bytes".getBytes(), 2, Instant.parse("2026-08-01T06:00:00Z"));
    }

    private OverTimeReport report(YearMonth yearMonth, int rowCount, long unclosedCount) {
        List<OverTimeReportData> rows = java.util.stream.IntStream.range(0, rowCount)
                .mapToObj(i -> new OverTimeReportData(
                        "EMP00" + i, "직원" + i, "백엔드팀", 60, 0, 0, 22500))
                .toList();
        return new OverTimeReport(yearMonth, rows, unclosedCount);
    }
}

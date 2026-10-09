package com.company.officecommute.domain.report;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 발송에 쓰는 최종 Excel 보관본. 재시도와 확정본 다운로드는 현재 데이터로 재생성하지 않고 이 파일을 쓴다.
 * 월·종류마다 한 번만 만들어지고 바뀌지 않는다.
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_file_year_month_kind", columnNames = {"target_year_month", "kind"})
})
public class ReportFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportFileId;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "target_year_month", nullable = false, length = 7)
    private YearMonth targetYearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportKind kind;

    @Column(nullable = false)
    private String fileName;

    // columnDefinition 을 고정해야 MySQL validate 가 V16 의 LONGBLOB 과 맞는다.
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(nullable = false)
    private long sizeBytes;

    // 재시도 메일 본문의 요약을 보관본과 같은 집계에서 가져오기 위해 함께 저장한다.
    @Column(nullable = false)
    private int employeeCount;

    @Column(nullable = false)
    private Instant createdAt;

    protected ReportFile() {
    }

    public ReportFile(
            YearMonth targetYearMonth,
            ReportKind kind,
            String fileName,
            byte[] content,
            int employeeCount,
            Instant createdAt
    ) {
        this.targetYearMonth = Objects.requireNonNull(targetYearMonth, "targetYearMonth는 null일 수 없습니다.");
        this.kind = Objects.requireNonNull(kind, "kind는 null일 수 없습니다.");
        this.fileName = Objects.requireNonNull(fileName, "fileName은 null일 수 없습니다.");
        Objects.requireNonNull(content, "content는 null일 수 없습니다.");
        if (content.length == 0) {
            throw new IllegalArgumentException("빈 보고서 파일은 보관할 수 없습니다.");
        }
        this.content = content.clone();
        this.sha256 = sha256Of(content);
        this.sizeBytes = content.length;
        this.employeeCount = employeeCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 null일 수 없습니다.");
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }

    public Long getReportFileId() {
        return reportFileId;
    }

    public YearMonth getTargetYearMonth() {
        return targetYearMonth;
    }

    public ReportKind getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content.clone();
    }

    public String getSha256() {
        return sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getEmployeeCount() {
        return employeeCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

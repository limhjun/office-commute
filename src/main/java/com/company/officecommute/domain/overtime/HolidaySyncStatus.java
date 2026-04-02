package com.company.officecommute.domain.overtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"year_value", "month_value"})
)
public class HolidaySyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_value", nullable = false)
    private int year;

    @Column(name = "month_value", nullable = false)
    private int month;

    @Column(nullable = false)
    private LocalDateTime lastSuccessfulSyncedAt;

    protected HolidaySyncStatus() {
    }

    public HolidaySyncStatus(int year, int month, LocalDateTime lastSuccessfulSyncedAt) {
        this.year = year;
        this.month = month;
        this.lastSuccessfulSyncedAt = lastSuccessfulSyncedAt;
    }

    public void markSyncedAt(LocalDateTime syncedAt) {
        this.lastSuccessfulSyncedAt = syncedAt;
    }

    public LocalDateTime getLastSuccessfulSyncedAt() {
        return lastSuccessfulSyncedAt;
    }
}

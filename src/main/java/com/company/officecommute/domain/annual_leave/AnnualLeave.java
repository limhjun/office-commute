package com.company.officecommute.domain.annual_leave;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "wanted_date"})
})
public class AnnualLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long employeeId;
    private LocalDate wantedDate;

    protected AnnualLeave() {
    }

    public AnnualLeave(Long employeeId, LocalDate wantedDate) {
        this(null, employeeId, wantedDate);
    }

    public AnnualLeave(Long id, Long employeeId, LocalDate wantedDate) {
        if (wantedDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(String.format("(%s)는 지난 날짜입니다.", wantedDate));
        }
        this.id = id;
        this.employeeId = employeeId;
        this.wantedDate = wantedDate;
    }

    public boolean isNotEnoughForEnroll(int annualLeaveCriteria) {
        return wantedDate.isBefore(LocalDate.now().plusDays(annualLeaveCriteria));
    }

    public boolean isRemain() {
        return wantedDate.isAfter(LocalDate.now());
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return wantedDate;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public LocalDate getWantedDate() {
        return wantedDate;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        AnnualLeave that = (AnnualLeave) object;
        return Objects.equals(employeeId, that.employeeId)
                && Objects.equals(wantedDate, that.wantedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId, wantedDate);
    }
}

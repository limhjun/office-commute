package com.company.officecommute.domain.annual_leave;

import java.util.ArrayList;
import java.util.List;

public class AnnualLeaves {

    private final List<AnnualLeave> annualLeaves;

    public AnnualLeaves(List<AnnualLeave> annualLeaves) {
        this.annualLeaves = new ArrayList<>(annualLeaves);
    }

    public void enroll(List<AnnualLeave> wantedLeaves) {
        if (hasDuplicate(wantedLeaves) || annualLeaves.stream().anyMatch(wantedLeaves::contains)) {
            throw new AnnualLeaveDuplicateException();
        }
        annualLeaves.addAll(wantedLeaves);
    }

    private boolean hasDuplicate(List<AnnualLeave> wantedLeaves) {
        return wantedLeaves.size() != wantedLeaves.stream()
                .distinct()
                .count();
    }

    public int numberOfLeaves() {
        return annualLeaves.size();
    }

    public boolean isMatchNotEnoughCriteria(int annualLeaveCriteria) {
        return this.annualLeaves.stream()
                .anyMatch(annualLeave
                        -> annualLeave.isNotEnoughForEnroll(annualLeaveCriteria));
    }

    public List<AnnualLeave> getAnnualLeaves() {
        return annualLeaves;
    }
}

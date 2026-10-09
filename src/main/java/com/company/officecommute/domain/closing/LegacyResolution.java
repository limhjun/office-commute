package com.company.officecommute.domain.closing;

public enum LegacyResolution {
    NO_CORRECTION_NEEDED(MonthlyClosingType.LEGACY_CONFIRMED),
    CORRECTED(MonthlyClosingType.LEGACY_CORRECTED);

    private final MonthlyClosingType closingType;

    LegacyResolution(MonthlyClosingType closingType) {
        this.closingType = closingType;
    }

    public MonthlyClosingType closingType() {
        return closingType;
    }
}

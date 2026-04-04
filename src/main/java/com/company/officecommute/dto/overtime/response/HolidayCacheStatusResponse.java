package com.company.officecommute.dto.overtime.response;

import java.time.LocalDateTime;

public record HolidayCacheStatusResponse(
        String yearMonth,
        int cachedHolidayCount,
        boolean cacheUsable,
        String status,
        String reason,
        LocalDateTime lastSuccessfulSyncedAt
) {
}

package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.web.ApiConvertor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class HolidaySyncService {

    private final ApiConvertor apiConvertor;
    private final HolidayCacheStatusService holidayCacheStatusService;

    public HolidaySyncService(
            ApiConvertor apiConvertor,
            HolidayCacheStatusService holidayCacheStatusService
    ) {
        this.apiConvertor = apiConvertor;
        this.holidayCacheStatusService = holidayCacheStatusService;
    }

    @Transactional
    public HolidayCacheStatusResponse refreshAndGetStatus(YearMonth yearMonth) {
        apiConvertor.refreshHolidays(yearMonth);
        return holidayCacheStatusService.getStatus(yearMonth);
    }
}

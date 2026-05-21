package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.Holiday;
import com.company.officecommute.domain.overtime.HolidayResponse;
import com.company.officecommute.domain.overtime.HolidaySyncStatus;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.repository.overtime.HolidayRepository;
import com.company.officecommute.repository.overtime.HolidaySyncStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Component
public class ApiConvertor {

    private static final Logger log = LoggerFactory.getLogger(ApiConvertor.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String HOLIDAY_DATA_UNAVAILABLE_MESSAGE =
            "공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;
    private final HolidayRepository holidayRepository;
    private final HolidaySyncStatusRepository holidaySyncStatusRepository;
    private final Clock clock;

    public ApiConvertor(
            RestTemplate restTemplate,
            ApiProperties apiProperties,
            HolidayRepository holidayRepository,
            HolidaySyncStatusRepository holidaySyncStatusRepository,
            Clock clock
    ) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
        this.holidayRepository = holidayRepository;
        this.holidaySyncStatusRepository = holidaySyncStatusRepository;
        this.clock = clock;
    }

    @Transactional
    public long countNumberOfStandardWorkingDays(YearMonth yearMonth) {
        Set<LocalDate> holidays = getHolidays(yearMonth);
        int lengthOfMonth = yearMonth.lengthOfMonth();
        long numberOfWeekends = WeekendCalculator.countNumberOfWeekends(yearMonth);
        long numberOfWeekDays = lengthOfMonth - numberOfWeekends;
        long numberOfHolidays = countWeekdayHolidays(holidays);

        return numberOfWeekDays - numberOfHolidays;
    }

    /**
     * 공휴일 데이터를 API에서 가져와 DB에 저장합니다.
     * 관리자 수동 동기화 API에서 호출하여 캐시를 갱신합니다.
     */
    @Transactional
    public void refreshHolidays(YearMonth yearMonth) {
        try {
            List<HolidayResponse.Item> items = fetchHolidaysFromApi(yearMonth);
            Set<LocalDate> holidays = convertToLocalDate(items);
            saveHolidaysToDatabase(yearMonth, holidays);
            log.info("공휴일 갱신 성공: {}-{}", yearMonth.getYear(), yearMonth.getMonthValue());
        } catch (Exception e) {
            log.warn("공휴일 갱신 실패. 기존 캐시 유지: {}-{}, 오류: {}",
                    yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage());
        }
    }

    private Set<LocalDate> getHolidays(YearMonth yearMonth) {
        try {
            List<HolidayResponse.Item> items = fetchHolidaysFromApi(yearMonth);
            Set<LocalDate> holidays = convertToLocalDate(items);
            saveHolidaysToDatabase(yearMonth, holidays);
            log.info("공휴일 API 호출 성공: {}-{}", yearMonth.getYear(), yearMonth.getMonthValue());
            return holidays;
        } catch (Exception e) {
            log.warn("공휴일 API 호출 실패. 리포트 생성을 중단합니다. yearMonth={}, error={}",
                    yearMonth, e.getMessage(), e);
            throw new HolidayDataUnavailableException(HOLIDAY_DATA_UNAVAILABLE_MESSAGE);
        }
    }

    private void saveHolidaysToDatabase(YearMonth yearMonth, Set<LocalDate> holidays) {
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        holidayRepository.deleteByYearAndMonth(year, month);

        List<Holiday> holidayEntities = holidays.stream()
                .map(date -> new Holiday(year, month, date))
                .toList();
        holidayRepository.saveAll(holidayEntities);

        HolidaySyncStatus syncStatus = holidaySyncStatusRepository.findByYearAndMonth(year, month)
                .orElseGet(() -> new HolidaySyncStatus(year, month, LocalDateTime.now(clock)));
        syncStatus.markSyncedAt(LocalDateTime.now(clock));
        holidaySyncStatusRepository.save(syncStatus);
    }

    private List<HolidayResponse.Item> fetchHolidaysFromApi(YearMonth yearMonth) {
        String solYear = String.valueOf(yearMonth.getYear());
        String solMonth = String.format("%02d", yearMonth.getMonthValue());
        String stringURL = apiProperties.combineURL(solYear, solMonth);
        URI uri;
        try {
            uri = new URI(stringURL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        HolidayResponse holidayResponse = restTemplate.getForObject(uri, HolidayResponse.class);
        if (holidayResponse == null || holidayResponse.getBody() == null) {
            throw new HolidayDataUnavailableException("공휴일 API 응답이 비정상입니다. yearMonth=" + yearMonth);
        }
        return holidayResponse.getBody().getItems();
    }

    private long countWeekdayHolidays(Set<LocalDate> holidays) {
        return holidays.stream()
                .filter(date -> !WeekendCalculator.isWeekend(date))
                .count();
    }

    private Set<LocalDate> convertToLocalDate(List<HolidayResponse.Item> items) {
        if (items == null) {
            return Set.of();
        }
        return items.stream()
                .map(item -> LocalDate.parse(item.getLocdate(), DATE_FORMATTER))
                .collect(toSet());
    }

    public long calculateStandardWorkingMinutes(long numberOfStandardWorkingDays) {
        return numberOfStandardWorkingDays * 8 * 60;
    }

}

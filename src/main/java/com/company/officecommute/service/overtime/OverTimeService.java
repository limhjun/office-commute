package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.web.ApiConvertor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OverTimeService {

    private static final String UNASSIGNED_TEAM_NAME = "미배정";

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final ApiConvertor apiConvertor;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            ApiConvertor apiConvertor
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.apiConvertor = apiConvertor;
    }

    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        ZonedDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZonedDateTime.now().getZone());

        List<TotalWorkingMinutes> totalWorkingMinutes = commuteHistoryRepository.findWithEmployeeIdByDateRange(startOfMonth, endOfMonth);
        Map<Long, TotalWorkingMinutes> totalWorkingMinutesByEmployeeId = totalWorkingMinutes.stream()
                .collect(Collectors.toMap(TotalWorkingMinutes::getEmployeeId, Function.identity(), (left, right) -> left));

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);
        long standardWorkingMinutes = apiConvertor.calculateStandardWorkingMinutes(numberOfStandardWorkingDays);

        return employeeRepository.findAllWithTeam().stream()
                .map(employee -> {
                    TotalWorkingMinutes totalWorkingMinute = totalWorkingMinutesByEmployeeId.get(employee.getEmployeeId());
                    if (totalWorkingMinute == null) {
                        return new OverTimeCalculateResponse(
                                employee.getEmployeeId(),
                                employee.getName(),
                                employee.getTeamName() != null ? employee.getTeamName() : UNASSIGNED_TEAM_NAME,
                                0L
                        );
                    }
                    return new OverTimeCalculateResponse(
                            totalWorkingMinute.getEmployeeId(),
                            totalWorkingMinute.getEmployeeName(),
                            totalWorkingMinute.getTeamName(),
                            totalWorkingMinute.calculateOverTime(standardWorkingMinutes)
                    );
                })
                .toList();
    }

}

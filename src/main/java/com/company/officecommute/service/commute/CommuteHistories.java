package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.DailyWorkDuration;
import com.company.officecommute.domain.commute.DailyWorkDurations;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;

import java.util.List;

public class CommuteHistories {

    private final List<CommuteHistory> commuteHistories;

    public CommuteHistories(List<CommuteHistory> commuteHistories) {
        this.commuteHistories = commuteHistories;
    }

    public WorkDurationPerDateResponse toWorkDurationPerDateResponse() {
        List<DailyWorkDuration> details = toDailyWorkDurations();
        long sumWorkingMinutes = new DailyWorkDurations(details).sumWorkingMinutes();
        // TODO 여기서 dto 변환 처리를 해줘도 되는지 생각해보기
        return new WorkDurationPerDateResponse(details, sumWorkingMinutes);
    }

    private List<DailyWorkDuration> toDailyWorkDurations() {
        return commuteHistories
                .stream()
                .map(CommuteHistory::toDailyWorkDuration)
                .toList();
    }
}

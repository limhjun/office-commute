package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.DailyWorkDuration;
import com.company.officecommute.domain.commute.DailyWorkDurations;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;

import java.util.List;
import java.util.function.Function;

public class CommuteHistories {

    private final List<CommuteHistory> commuteHistories;

    public CommuteHistories(List<CommuteHistory> commuteHistories) {
        this.commuteHistories = commuteHistories;
    }

    public WorkDurationPerDateResponse toWorkDurationPerDateResponse(
            Function<CommuteHistory, CommuteDetailResponse> toDetail
    ) {
        long sumWorkingMinutes = new DailyWorkDurations(toDailyWorkDurations()).sumWorkingMinutes();
        return new WorkDurationPerDateResponse(toDetails(toDetail), sumWorkingMinutes);
    }

    private List<CommuteDetailResponse> toDetails(Function<CommuteHistory, CommuteDetailResponse> toDetail) {
        return commuteHistories
                .stream()
                .map(toDetail)
                .toList();
    }

    public List<Long> commuteHistoryIds() {
        return commuteHistories.stream()
                .map(CommuteHistory::getCommuteHistoryId)
                .toList();
    }

    private List<DailyWorkDuration> toDailyWorkDurations() {
        return commuteHistories
                .stream()
                .map(CommuteHistory::toDailyWorkDuration)
                .toList();
    }
}

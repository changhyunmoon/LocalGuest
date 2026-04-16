package com.team6.domain.guestMyPage.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MyPageMainResponse {
    // 프로필 정보
    private String guestName;
    private String guestEmail;

    // 다가오는 여행 일정 (Matching)
    private List<UpcomingMatchResponse> upcomingMatches;

    // 나의 여행 기록 - 스크랩북
    private List<ScrapbookSummaryResponse> scrapbooks;
}

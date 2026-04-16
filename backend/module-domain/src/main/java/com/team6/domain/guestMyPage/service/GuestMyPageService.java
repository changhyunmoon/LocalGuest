package com.team6.domain.guestMyPage.service;

import com.team6.domain.guestMyPage.dto.request.ScrapbookCreateRequest;
import com.team6.domain.guestMyPage.dto.response.MyPageMainResponse;
import com.team6.domain.guestMyPage.dto.response.ScrapbookSummaryResponse;
import com.team6.domain.guestMyPage.dto.response.UpcomingMatchResponse;
import com.team6.domain.guestMyPage.entity.Scrapbook;
import com.team6.domain.guestMyPage.repository.ScrapbookRepository;
import com.team6.domain.matching.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestMyPageService {

    private final MatchRequestRepository matchRequestRepository;
    private final ScrapbookRepository scrapbookRepository;
    // private final MemberRepository memberRepository;

    public MyPageMainResponse getDashBoard(Long guestId) {
        // 다가오는 여행 가져오기(PAID 상태인 것들만)
        List<UpcomingMatchResponse> upcomingMatches = matchRequestRepository
                .findByGuestId(guestId).stream()
                .filter(m -> "PAID".equals(m.getStatus().name()))
                .map(UpcomingMatchResponse::from)
                .toList();

        // 내 스크랩북 가져오기
        List<ScrapbookSummaryResponse> scrapbooks = scrapbookRepository
                .findByGuestIdOrderByCreatedAtDesc(guestId).stream()
                .map(ScrapbookSummaryResponse::from)
                .toList();

        return MyPageMainResponse.builder()
                .guestName(memberRepository)
    }

}

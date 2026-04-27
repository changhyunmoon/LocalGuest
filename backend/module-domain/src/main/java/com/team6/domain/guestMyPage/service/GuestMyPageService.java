package com.team6.domain.guestMyPage.service;

import com.team6.domain.guestMyPage.dto.request.ScrapbookCreateRequest;
import com.team6.domain.guestMyPage.dto.response.MyPageMainResponse;
import com.team6.domain.guestMyPage.dto.response.ScrapbookSummaryResponse;
import com.team6.domain.guestMyPage.dto.response.UpcomingMatchResponse;
import com.team6.domain.guestMyPage.entity.Scrapbook;
import com.team6.domain.guestMyPage.repository.ScrapbookRepository;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;

    public MyPageMainResponse getDashBoard(Long guestId) {
        Member member = memberRepository.findById(guestId)
                .orElseThrow(()-> new IllegalArgumentException("존재하지 않는 회원입니다. "));

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

        // 실제 회원 데이터를 넣어 조립해서 반환
        return MyPageMainResponse.builder()
                .guestName(member.getNickname())
                .guestEmail(member.getEmail())
                .upcomingMatches(upcomingMatches)
                .scrapbooks(scrapbooks)
                .build();
    }

}

package com.team6.domain.guestMyPage.service;

import com.team6.domain.guestMyPage.dto.request.ScrapbookCreateRequest;
import com.team6.domain.guestMyPage.dto.response.ScrapbookSummaryResponse;
import com.team6.domain.guestMyPage.entity.Scrapbook;
import com.team6.domain.guestMyPage.repository.ScrapbookRepository;
import com.team6.domain.matching.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ScrapbookService {
    private final ScrapbookRepository scrapbookRepository;
    private final MatchRequestRepository matchRequestRepository;

    public ScrapbookSummaryResponse createScrapbook(Long guestId, ScrapbookCreateRequest request){
        //매칭 내역 존재 여부 및 본인 확인
        matchRequestRepository.findById(request.getMatchRequestId())
                .filter(m -> m.getGuestId().equals(guestId))
                .orElseThrow(() -> new IllegalArgumentException("권한이 없거나 존재하지 않는 여행입니다. "));

        // 중복 작성 방지
        if(scrapbookRepository.existsByMatchRequestId(request.getMatchRequestId())){
            throw new IllegalArgumentException("이미 이 여행에 대한 스크랩북을 작성하셨습니다. ");
        }

        Scrapbook scrapbook = Scrapbook.builder()
                .guestId(guestId)
                .matchRequestId(request.getMatchRequestId())
                .title(request.getTitle())
                .content(request.getContent())
                .mainImageUrl(request.getMainImgaeUrl())
                .tags(request.getTags())
                .build();

        Scrapbook saved = scrapbookRepository.save(scrapbook);
        return ScrapbookSummaryResponse.from(saved);

    }
}

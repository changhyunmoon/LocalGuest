package com.team6.domain.guestMyPage.controller;

import com.team6.domain.guestMyPage.dto.request.ScrapbookCreateRequest;
import com.team6.domain.guestMyPage.dto.response.MyPageMainResponse;
import com.team6.domain.guestMyPage.dto.response.ScrapbookSummaryResponse;
import com.team6.domain.guestMyPage.service.GuestMyPageService;
import com.team6.domain.guestMyPage.service.ScrapbookService;
import com.team6.domain.matching.support.MatchingAuthenticationSupport;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mypage/guest")
@RequiredArgsConstructor
public class GuestMyPageController {
    private final GuestMyPageService guestMyPageService;
    private final ScrapbookService scrapbookService;
    private final MatchingAuthenticationSupport authenticationSupport;

    // 마이페이지 메인 대시보드 조회
    @GetMapping("/dashboard")
    public ResponseEntity<MyPageMainResponse> getDashboard() {
        Long guestId = authenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(guestMyPageService.getDashBoard(guestId));
    }

    @PostMapping("/scrapbooks")
    public ResponseEntity<ScrapbookSummaryResponse> createScrapbook (
            @RequestBody @Valid ScrapbookCreateRequest request
            ){
        Long guestId = authenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(scrapbookService.createScrapbook(guestId, request));
    }
}

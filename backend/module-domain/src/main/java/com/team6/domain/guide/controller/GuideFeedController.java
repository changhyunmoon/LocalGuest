package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideFeedRequest;
import com.team6.domain.guide.dto.request.UpdateGuideFeedRequest;
import com.team6.domain.guide.dto.response.GuideFeedResponse;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.service.GuideFeedService;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 가이드 피드 컨트롤러 (F06-03)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/guides/{guideId}/feeds")
public class GuideFeedController {

    private final GuideFeedService guideFeedService;
    private final MemberRepository memberRepository;

    // 피드 등록
    @PostMapping
    public ResponseEntity<GuideFeedResponse> createFeed(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideFeedRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guideFeedService.createFeed(guideId, request, userId));
    }

    // 피드 목록 조회 — 전체 공개 (F06-03)
    @GetMapping
    public ResponseEntity<List<GuideFeedResponse>> getFeeds(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideFeedService.getFeeds(guideId));
    }

    // 피드 수정
    @PutMapping("/{feedId}")
    public ResponseEntity<GuideFeedResponse> updateFeed(
            @PathVariable Long guideId,
            @PathVariable Long feedId,
            @RequestBody @Valid UpdateGuideFeedRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideFeedService.updateFeed(guideId, feedId, request, userId));
    }

    // 피드 소프트 삭제
    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @PathVariable Long guideId,
            @PathVariable Long feedId
    ) {
        Long userId = getCurrentUserId();
        guideFeedService.deleteFeed(guideId, feedId, userId);
        return ResponseEntity.noContent().build();
    }

    // JWT에서 현재 로그인 사용자의 memberId 추출
    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserEmail();
        return memberRepository.findByEmail(email)
                .map(Member::getId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.MEMBER_NOT_FOUND));
    }
}

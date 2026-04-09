package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideFeedRequest;
import com.team6.domain.guide.dto.request.UpdateGuideFeedRequest;
import com.team6.domain.guide.dto.response.GuideFeedResponse;
import com.team6.domain.guide.service.GuideFeedService;
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

    // 피드 등록
    @PostMapping
    public ResponseEntity<GuideFeedResponse> createFeed(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideFeedRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guideFeedService.createFeed(guideId, request, userId));
    }

    // 피드 목록 조회 — isMatched로 공개 범위 결정 (F06-03)
    @GetMapping
    public ResponseEntity<List<GuideFeedResponse>> getFeeds(
            @PathVariable Long guideId,
            @RequestParam(defaultValue = "false") boolean isMatched // 매칭 여부 (기본값: 미매칭)
    ) {
        return ResponseEntity.ok(guideFeedService.getFeeds(guideId, isMatched));
    }

    // 피드 수정
    @PutMapping("/{feedId}")
    public ResponseEntity<GuideFeedResponse> updateFeed(
            @PathVariable Long guideId,
            @PathVariable Long feedId,
            @RequestBody @Valid UpdateGuideFeedRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideFeedService.updateFeed(guideId, feedId, request, userId));
    }

    // 피드 소프트 삭제
    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @PathVariable Long guideId,
            @PathVariable Long feedId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        guideFeedService.deleteFeed(guideId, feedId, userId);
        return ResponseEntity.noContent().build();
    }
}

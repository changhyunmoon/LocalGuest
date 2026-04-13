package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideRatingRequest;
import com.team6.domain.guide.dto.response.GuideProfileResponse;
import com.team6.domain.guide.dto.response.GuideReviewSummaryResponse;
import com.team6.domain.guide.dto.response.GuideSettlementResponse;
import com.team6.domain.guide.service.GuideProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 가이드 프로필 컨트롤러 (F06-01, F06-06)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/guides")
public class GuideProfileController {

    private final GuideProfileService guideProfileService;

    // 가이드 프로필 등록 (F06-01)
    @PostMapping
    public ResponseEntity<GuideProfileResponse> createProfile(
            @RequestBody @Valid CreateGuideProfileRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guideProfileService.createProfile(request, userId));
    }

    // 가이드 프로필 단건 조회
    @GetMapping("/{guideId}")
    public ResponseEntity<GuideProfileResponse> getProfile(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideProfileService.getProfile(guideId));
    }

    // 가이드 프로필 목록 조회
    @GetMapping
    public ResponseEntity<List<GuideProfileResponse>> getProfileList() {
        return ResponseEntity.ok(guideProfileService.getProfileList());
    }

    // 가이드 프로필 수정 (F06-01)
    @PutMapping("/{guideId}")
    public ResponseEntity<GuideProfileResponse> updateProfile(
            @PathVariable Long guideId,
            @RequestBody @Valid UpdateGuideProfileRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideProfileService.updateProfile(guideId, request, userId));
    }

    // 정산 예정 금액 조회 — 본인만 조회 가능 (F06-05)
    @GetMapping("/{guideId}/settlement/expected")
    public ResponseEntity<GuideSettlementResponse> getExpectedSettlement(
            @PathVariable Long guideId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideProfileService.getExpectedSettlement(guideId, userId));
    }

    // 가이드 리뷰 요약 조회 — averageRating, reviewCount 반환 (F06-07)
    @GetMapping("/{guideId}/reviews/summary")
    public ResponseEntity<GuideReviewSummaryResponse> getReviewSummary(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideProfileService.getReviewSummary(guideId));
    }

    // 평균 평점 및 리뷰 수 갱신 — review 도메인이 리뷰 작성/삭제 후 호출 (F06-07)
    @PatchMapping("/{guideId}/rating")
    public ResponseEntity<GuideProfileResponse> updateRating(
            @PathVariable Long guideId,
            @RequestBody @Valid UpdateGuideRatingRequest request
    ) {
        return ResponseEntity.ok(guideProfileService.updateRating(guideId, request));
    }

    // 가이드 활성화/비활성화 토글 (F06-06)
    @PatchMapping("/{guideId}/active")
    public ResponseEntity<GuideProfileResponse> toggleActive(
            @PathVariable Long guideId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideProfileService.toggleActive(guideId, userId));
    }
}

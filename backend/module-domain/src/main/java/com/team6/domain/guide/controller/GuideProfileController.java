package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideProfileRequest;
import com.team6.domain.guide.dto.response.GuideProfileResponse;
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
@RequestMapping("/api/guides")
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

    // 가이드 활성화/비활성화 토글 (F06-06)
    @PatchMapping("/{guideId}/active")
    public ResponseEntity<GuideProfileResponse> toggleActive(
            @PathVariable Long guideId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideProfileService.toggleActive(guideId, userId));
    }
}

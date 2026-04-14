package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideImageRequest;
import com.team6.domain.guide.dto.response.GuideImageResponse;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.service.GuideImageService;
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
 * 가이드 이미지(피드) 컨트롤러 (F06-03)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/guides/{guideId}/images")
public class GuideImageController {

    private final GuideImageService guideImageService;
    private final MemberRepository memberRepository;

    // 이미지 등록 (F06-03)
    @PostMapping
    public ResponseEntity<GuideImageResponse> addImage(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideImageRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guideImageService.addImage(guideId, request, userId));
    }

    // 이미지 목록 조회 — 정렬 순서대로 (F06-03)
    @GetMapping
    public ResponseEntity<List<GuideImageResponse>> getImages(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideImageService.getImages(guideId));
    }

    // 이미지 삭제 (F06-03)
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long guideId,
            @PathVariable Long imageId
    ) {
        Long userId = getCurrentUserId();
        guideImageService.deleteImage(imageId, guideId, userId);
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

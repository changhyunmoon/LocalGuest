package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideImageRequest;
import com.team6.domain.guide.dto.response.GuideImageResponse;
import com.team6.domain.guide.service.GuideImageService;
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
@RequestMapping("/api/guides/{guideId}/images")
public class GuideImageController {

    private final GuideImageService guideImageService;

    // 이미지 등록 (F06-03)
    @PostMapping
    public ResponseEntity<GuideImageResponse> addImage(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideImageRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
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
            @PathVariable Long imageId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        guideImageService.deleteImage(imageId, guideId, userId);
        return ResponseEntity.noContent().build();
    }
}

package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideCareerRequest;
import com.team6.domain.guide.dto.request.UpdateGuideCareerRequest;
import com.team6.domain.guide.dto.response.GuideCareerResponse;
import com.team6.domain.guide.service.GuideCareerService;
import com.team6.domain.guide.support.GuideAuthenticationSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 가이드 경력 컨트롤러 (F06-01)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/guides/{guideId}/careers")
public class GuideCareerController {

    private final GuideCareerService guideCareerService;
    private final GuideAuthenticationSupport guideAuthenticationSupport;

    // 경력 등록
    @PostMapping
    public ResponseEntity<GuideCareerResponse> addCareer(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideCareerRequest request
    ) {
        Long userId = guideAuthenticationSupport.getCurrentGuideMemberId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guideCareerService.addCareer(guideId, request, userId));
    }

    // 경력 목록 조회
    @GetMapping
    public ResponseEntity<List<GuideCareerResponse>> getCareers(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideCareerService.getCareers(guideId));
    }

    // 경력 수정
    @PutMapping("/{careerId}")
    public ResponseEntity<GuideCareerResponse> updateCareer(
            @PathVariable Long guideId,
            @PathVariable Long careerId,
            @RequestBody @Valid UpdateGuideCareerRequest request
    ) {
        Long userId = guideAuthenticationSupport.getCurrentGuideMemberId();
        return ResponseEntity.ok(guideCareerService.updateCareer(careerId, guideId, request, userId));
    }

    // 경력 삭제
    @DeleteMapping("/{careerId}")
    public ResponseEntity<Void> deleteCareer(
            @PathVariable Long guideId,
            @PathVariable Long careerId
    ) {
        Long userId = guideAuthenticationSupport.getCurrentGuideMemberId();
        guideCareerService.deleteCareer(careerId, guideId, userId);
        return ResponseEntity.noContent().build();
    }
}

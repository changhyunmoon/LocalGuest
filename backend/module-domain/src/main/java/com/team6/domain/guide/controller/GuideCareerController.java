package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideCareerRequest;
import com.team6.domain.guide.dto.request.UpdateGuideCareerRequest;
import com.team6.domain.guide.dto.response.GuideCareerResponse;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.service.GuideCareerService;
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
 * 가이드 경력 컨트롤러 (F06-01)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/guides/{guideId}/careers")
public class GuideCareerController {

    private final GuideCareerService guideCareerService;
    private final MemberRepository memberRepository;

    // 경력 등록
    @PostMapping
    public ResponseEntity<GuideCareerResponse> addCareer(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideCareerRequest request
    ) {
        Long userId = getCurrentUserId();
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
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideCareerService.updateCareer(careerId, guideId, request, userId));
    }

    // 경력 삭제
    @DeleteMapping("/{careerId}")
    public ResponseEntity<Void> deleteCareer(
            @PathVariable Long guideId,
            @PathVariable Long careerId
    ) {
        Long userId = getCurrentUserId();
        guideCareerService.deleteCareer(careerId, guideId, userId);
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

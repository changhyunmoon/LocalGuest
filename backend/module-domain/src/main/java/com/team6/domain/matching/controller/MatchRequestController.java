package com.team6.domain.matching.controller;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.dto.request.CancelRequestDto;
import com.team6.domain.matching.dto.request.MatchRequestDeclineRequest;
import com.team6.domain.matching.dto.request.MatchRequestCreateRequest;
import com.team6.domain.matching.dto.request.MatchRequestProposeRequest;
import com.team6.domain.matching.dto.response.MatchRequestActionResponse;
import com.team6.domain.matching.dto.response.MatchRequestCreateResponse;
import com.team6.domain.matching.service.MatchRequestService;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/matching/requests")
@RequiredArgsConstructor
public class MatchRequestController {

    private final MatchRequestService matchRequestService;
    private final MemberRepository memberRepository;
    private final GuideProfileRepository guideProfileRepository;

    @PostMapping
    public ResponseEntity<MatchRequestCreateResponse> createMatchRequest(
            @RequestBody @Valid MatchRequestCreateRequest request
    ) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        if (!guideProfileRepository.existsById(request.getGuideId())) {
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        MatchRequestCreateResponse saved = matchRequestService.createMatchRequest(guestId, request);

        return ResponseEntity.ok(saved);
    }

    /**
     * 게스트 본인 매칭 요청 목록 (마이페이지).
     * {@code /guests/me} 처럼 한 세그먼트 경로는 정적 리소스·패턴 충돌이 나기 쉬워 {@code /guest/list} 로 고정한다.
     */
    @GetMapping("/guest/list")
    public ResponseEntity<List<MatchRequestCreateResponse>> getGuestRequests() {
        Long guestId = getCurrentMemberId(Role.GUEST);
        return ResponseEntity.ok(matchRequestService.getGuestRequests(guestId));
    }

    /** 가이드 본인 매칭 요청 목록 — 경로는 {@code /guide/list} (게스트 {@code /guest/list} 와 대칭). */
    @GetMapping("/guide/list")
    public ResponseEntity<List<MatchRequestCreateResponse>> getGuideRequests() {
        Long guideId = getCurrentGuideProfileId();
        return ResponseEntity.ok(matchRequestService.getGuideRequests(guideId));
    }

    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<MatchRequestActionResponse> rejectMatchRequest(
            @PathVariable Long requestId
    ) {
        Long guideId = getCurrentGuideProfileId();
        MatchRequestActionResponse updated = matchRequestService.rejectMatchRequest(guideId, requestId);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/propose")
    public ResponseEntity<MatchRequestActionResponse> proposeMatchRequest(
            @PathVariable Long requestId,
            @RequestBody @Valid MatchRequestProposeRequest request
    ) {
        Long guideId = getCurrentGuideProfileId();
        MatchRequestActionResponse updated = matchRequestService.proposeMatchRequest(guideId, requestId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/accept")
    public ResponseEntity<MatchRequestActionResponse> acceptMatchRequest(
            @PathVariable Long requestId
    ) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        MatchRequestActionResponse updated = matchRequestService.acceptMatchRequest(guestId, requestId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 게스트가 가이드의 제시안을 최종 거절하는 API (F03-06)
     */
    @PatchMapping("/{requestId}/decline")
    public ResponseEntity<MatchRequestActionResponse> declineMatchRequest(
            @PathVariable Long requestId,
            @RequestBody @Valid MatchRequestDeclineRequest request
    ) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        MatchRequestActionResponse updated = matchRequestService.declineMatchRequest(guestId, requestId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/guest/cancel")
    public ResponseEntity<MatchRequestActionResponse> cancelByGuest(
            @PathVariable Long requestId,
            @RequestBody @Valid CancelRequestDto request
    ) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        MatchRequestActionResponse updated =
                matchRequestService.cancelByGuest(guestId, requestId, request.getCancelReason());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/guide/cancel")
    public ResponseEntity<MatchRequestActionResponse> cancelByGuide(
            @PathVariable Long requestId,
            @RequestBody @Valid CancelRequestDto request
    ) {
        Long guideId = getCurrentGuideProfileId();
        MatchRequestActionResponse updated =
                matchRequestService.cancelByGuide(guideId, requestId, request.getCancelReason());
        return ResponseEntity.ok(updated);
    }

    private Long getCurrentMemberId(Role requiredRole) {
        String email = SecurityUtil.getCurrentUserEmail();
        return memberRepository.findByEmail(email)
                .map(member -> validateAndGetMemberId(member, requiredRole))
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }

    private Long validateAndGetMemberId(Member member, Role requiredRole) {
        if (requiredRole != null && member.getRole() != requiredRole) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        return member.getId();
    }

    private Long getCurrentGuideProfileId() {
        Long memberId = getCurrentMemberId(Role.GUIDE);
        return guideProfileRepository.findByMemberId(memberId)
                .map(guideProfile -> guideProfile.getId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }
}

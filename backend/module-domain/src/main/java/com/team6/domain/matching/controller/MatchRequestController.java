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
import com.team6.domain.matching.support.MatchingAuthenticationSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/matching/requests")
@RequiredArgsConstructor
public class MatchRequestController {

    private final MatchRequestService matchRequestService;
    private final GuideProfileRepository guideProfileRepository;
    private final MatchingAuthenticationSupport matchingAuthenticationSupport;

    @PostMapping
    public ResponseEntity<MatchRequestCreateResponse> createMatchRequest(
            @RequestBody @Valid MatchRequestCreateRequest request
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
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
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(matchRequestService.getGuestRequests(guestId));
    }

    /** 게스트 본인 매칭 요청 페이징 조회 (기존 /guest/list 하위호환 유지용 신규 경로). */
    @GetMapping("/guest/list/paged")
    public ResponseEntity<Page<MatchRequestCreateResponse>> getGuestRequestsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(matchRequestService.getGuestRequestsPaged(guestId, page, size));
    }

    /**
     * 게스트 본인 매칭 요청 Slice 조회.
     * 기존 /guest/list 응답(List)은 그대로 유지하여 하위호환을 보장한다.
     */
    @GetMapping("/guest/list/slice")
    public ResponseEntity<Slice<MatchRequestCreateResponse>> getGuestRequestsSlice(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(matchRequestService.getGuestRequestsSlice(guestId, page, size));
    }

    /**
     * 게스트 본인 매칭 요청 Slice 조회 (DTO projection).
     * 기존 API 동작은 유지하고, 성능 비교를 위한 별도 경로를 제공한다.
     */
    @GetMapping("/guest/list/slice-projected")
    public ResponseEntity<Slice<MatchRequestCreateResponse>> getGuestRequestsSliceProjected(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(matchRequestService.getGuestRequestsSliceProjected(guestId, page, size));
    }

    /** 가이드 본인 매칭 요청 목록 — 경로는 {@code /guide/list} (게스트 {@code /guest/list} 와 대칭). */
    @GetMapping("/guide/list")
    public ResponseEntity<List<MatchRequestCreateResponse>> getGuideRequests() {
        Long guideId = matchingAuthenticationSupport.getCurrentGuideProfileId();
        return ResponseEntity.ok(matchRequestService.getGuideRequests(guideId));
    }

    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<MatchRequestActionResponse> rejectMatchRequest(
            @PathVariable Long requestId
    ) {
        Long guideId = matchingAuthenticationSupport.getCurrentGuideProfileId();
        MatchRequestActionResponse updated = matchRequestService.rejectMatchRequest(guideId, requestId);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/propose")
    public ResponseEntity<MatchRequestActionResponse> proposeMatchRequest(
            @PathVariable Long requestId,
            @RequestBody @Valid MatchRequestProposeRequest request
    ) {
        Long guideId = matchingAuthenticationSupport.getCurrentGuideProfileId();
        MatchRequestActionResponse updated = matchRequestService.proposeMatchRequest(guideId, requestId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/accept")
    public ResponseEntity<MatchRequestActionResponse> acceptMatchRequest(
            @PathVariable Long requestId
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
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
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        MatchRequestActionResponse updated = matchRequestService.declineMatchRequest(guestId, requestId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/guest/cancel")
    public ResponseEntity<MatchRequestActionResponse> cancelByGuest(
            @PathVariable Long requestId,
            @RequestBody @Valid CancelRequestDto request
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        MatchRequestActionResponse updated =
                matchRequestService.cancelByGuest(guestId, requestId, request.getCancelReason());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{requestId}/guide/cancel")
    public ResponseEntity<MatchRequestActionResponse> cancelByGuide(
            @PathVariable Long requestId,
            @RequestBody @Valid CancelRequestDto request
    ) {
        Long guideId = matchingAuthenticationSupport.getCurrentGuideProfileId();
        MatchRequestActionResponse updated =
                matchRequestService.cancelByGuide(guideId, requestId, request.getCancelReason());
        return ResponseEntity.ok(updated);
    }
}

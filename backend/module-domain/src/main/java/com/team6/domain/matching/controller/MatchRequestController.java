package com.team6.domain.matching.controller;

import com.team6.domain.matching.dto.request.MatchRequestCreateRequest;
import com.team6.domain.matching.dto.response.MatchRequestActionResponse;
import com.team6.domain.matching.dto.response.MatchRequestCreateResponse;
import com.team6.domain.matching.service.MatchRequestService;
import com.team6.domain.matching.entity.MatchRequest;
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

    @PostMapping
    public ResponseEntity<MatchRequestCreateResponse> createMatchRequest(
            @RequestBody @Valid MatchRequestCreateRequest request
    ) {
        Long guestId = 1L; // TODO: JWT 연동 후 교체

        MatchRequestCreateResponse saved = matchRequestService.createMatchRequest(guestId, request);

        return ResponseEntity.ok(saved);
    }

    /**
     * 가이드가 자신에게 들어온 매칭 요청 목록을 조회하는 API
     *
     * 현재는 guideId를 path variable로 받아 테스트한다.
     * 추후 JWT 연동 시 guideId는 인증 정보에서 추출하도록 변경 가능하다.
     */
    @GetMapping("/guides/{guideId}")
    public ResponseEntity<List<MatchRequestCreateResponse>> getGuideRequests(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(matchRequestService.getGuideRequests(guideId));
    }

    /**
     * 가이드가 특정 매칭 요청을 거절하는 API
     *
     * 현재는 guideId를 request param으로 받아 권한을 검증한다.
     * 추후 JWT 연동 시 guideId는 인증 정보에서 추출하도록 변경 가능하다.
     */
    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<MatchRequestActionResponse> rejectMatchRequest(
            @PathVariable Long requestId,
            @RequestParam Long guideId
    ) {
        MatchRequestActionResponse updated = matchRequestService.rejectMatchRequest(guideId, requestId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 가이드가 특정 매칭 요청을 제안 단계(PROPOSED)로 변경하는 API
     *
     * 현재는 guideId를 request param으로 받아 권한을 검증한다.
     * 추후 JWT 연동 시 guideId는 인증 정보에서 추출하도록 변경 가능하다.
     *
     * 주의:
     * 현재 MatchRequest 엔티티에는 실제 제안 내용(일정/메시지) 저장 필드가 없어서
     * 우선 상태만 PENDING -> PROPOSED 로 변경한다.
     */
    @PatchMapping("/{requestId}/propose")
    public ResponseEntity<MatchRequestActionResponse> proposeMatchRequest(
            @PathVariable Long requestId,
            @RequestParam Long guideId
    ) {
        MatchRequestActionResponse updated = matchRequestService.proposeMatchRequest(guideId, requestId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 게스트가 가이드의 제안을 최종 수락하는 API
     *
     * 현재는 guestId를 request param으로 받아 권한을 검증한다.
     * 추후 JWT 연동 시 guestId는 인증 정보에서 추출하도록 변경 가능하다.
     *
     * 현재 상태가 PROPOSED일 때만 ACCEPTED로 변경된다.
     */
    @PatchMapping("/{requestId}/accept")
    public ResponseEntity<MatchRequestActionResponse> acceptMatchRequest(
            @PathVariable Long requestId,
            @RequestParam Long guestId
    ) {
        MatchRequestActionResponse updated = matchRequestService.acceptMatchRequest(guestId, requestId);
        return ResponseEntity.ok(updated);
    }
}
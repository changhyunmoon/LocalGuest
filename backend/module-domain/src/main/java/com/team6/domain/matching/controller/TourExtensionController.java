package com.team6.domain.matching.controller;

import com.team6.domain.matching.dto.request.TourExtensionSelectRequest;
import com.team6.domain.matching.dto.response.TourExtensionResponseDto;
import com.team6.domain.matching.service.TourExtensionService;
import com.team6.domain.matching.support.MatchingAuthenticationSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/matching/extensions")
public class TourExtensionController {

    private final TourExtensionService tourExtensionService;
    private final MatchingAuthenticationSupport matchingAuthenticationSupport;

    @GetMapping("/{requestId}")
    public ResponseEntity<TourExtensionResponseDto> getExtension(
            @PathVariable Long requestId
    ) {
        Long actorId = matchingAuthenticationSupport.resolveTourExtensionActorId();
        return ResponseEntity.ok(tourExtensionService.getByRequestId(requestId, actorId));
    }

    // 게스트 전용 연장 선택 API
    @PatchMapping("/{requestId}/select")
    public ResponseEntity<TourExtensionResponseDto> selectByGuest(
            @PathVariable Long requestId,
            @RequestBody @Valid TourExtensionSelectRequest request
    ) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(tourExtensionService.selectByGuest(guestId, requestId, request));
    }
}

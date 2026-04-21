package com.team6.domain.matching.controller;

import com.team6.domain.matching.dto.request.TourExtensionSelectRequest;
import com.team6.domain.matching.dto.response.TourExtensionResponseDto;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
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
        try {
            return ResponseEntity.ok(tourExtensionService.getByRequestId(requestId, actorId));
        } catch (MatchingException e) {
            if (e.getErrorCode() == MatchingErrorCode.TOUR_EXTENSION_GUIDE_NEXT_DAY_BOOKED) {
                return ResponseEntity.noContent()
                        .header("X-Extension-Reason", "GUIDE_NEXT_DAY_BOOKED")
                        .build();
            }
            if (e.getErrorCode() == MatchingErrorCode.TOUR_EXTENSION_GUIDE_NEXT_DAY_BLOCKED) {
                return ResponseEntity.noContent()
                        .header("X-Extension-Reason", "GUIDE_NEXT_DAY_BLOCKED")
                        .build();
            }
            if (e.getErrorCode() == MatchingErrorCode.TOUR_EXTENSION_GUIDE_UNAVAILABLE_NEXT_DAY) {
                return ResponseEntity.noContent()
                        .header("X-Extension-Reason", "GUIDE_NEXT_DAY_UNAVAILABLE")
                        .build();
            }
            if (e.getErrorCode() == MatchingErrorCode.TOUR_EXTENSION_ALREADY_DECIDED) {
                return ResponseEntity.noContent()
                        .header("X-Extension-Reason", "ALREADY_DECIDED")
                        .build();
            }
            if (e.getErrorCode() == MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND) {
                // 연장 대상이 아닌 경우(정상 시나리오)는 404 대신 204로 응답해
                // 클라이언트에서 "오류"로 보이지 않도록 한다.
                return ResponseEntity.noContent().build();
            }
            throw e;
        }
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

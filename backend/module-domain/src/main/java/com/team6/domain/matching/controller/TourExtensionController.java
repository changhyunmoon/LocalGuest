package com.team6.domain.matching.controller;

import com.team6.domain.matching.dto.request.TourExtensionSelectRequest;
import com.team6.domain.matching.dto.response.TourExtensionResponseDto;
import com.team6.domain.matching.service.TourExtensionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/matching/extensions")
public class TourExtensionController {

    private final TourExtensionService tourExtensionService;

    @GetMapping("/{requestId}")
    public ResponseEntity<TourExtensionResponseDto> getExtension(
            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(tourExtensionService.getByRequestId(requestId));
    }

    // 게스트 전용 연장 선택 API (임시: guestId는 request param)
    @PatchMapping("/{requestId}/select")
    public ResponseEntity<TourExtensionResponseDto> selectByGuest(
            @PathVariable Long requestId,
            @RequestParam Long guestId,
            @RequestBody @Valid TourExtensionSelectRequest request
    ) {
        // TODO: 인증 모듈 연동 시 guestId는 토큰 클레임에서 추출한다.
        return ResponseEntity.ok(tourExtensionService.selectByGuest(guestId, requestId, request));
    }
}

package com.team6.domain.matching.controller;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.dto.request.TourExtensionSelectRequest;
import com.team6.domain.matching.dto.response.TourExtensionResponseDto;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.service.TourExtensionService;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
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
    private final MemberRepository memberRepository;
    private final GuideProfileRepository guideProfileRepository;

    @GetMapping("/{requestId}")
    public ResponseEntity<TourExtensionResponseDto> getExtension(
            @PathVariable Long requestId
    ) {
        Member member = getCurrentMember();
        Long actorId = resolveActorIdForExtensionRead(member);
        return ResponseEntity.ok(tourExtensionService.getByRequestId(requestId, actorId));
    }

    // 게스트 전용 연장 선택 API
    @PatchMapping("/{requestId}/select")
    public ResponseEntity<TourExtensionResponseDto> selectByGuest(
            @PathVariable Long requestId,
            @RequestBody @Valid TourExtensionSelectRequest request
    ) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        return ResponseEntity.ok(tourExtensionService.selectByGuest(guestId, requestId, request));
    }

    private Long getCurrentMemberId(Role requiredRole) {
        return validateAndGetMemberId(getCurrentMember(), requiredRole);
    }

    private Member getCurrentMember() {
        String email = SecurityUtil.getCurrentUserEmail();
        return memberRepository.findByEmail(email)
                .map(member -> member)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }

    private Long validateAndGetMemberId(Member member, Role requiredRole) {
        if (requiredRole != null && member.getRole() != requiredRole) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        return member.getId();
    }

    private Long resolveActorIdForExtensionRead(Member member) {
        if (member.getRole() == Role.GUIDE) {
            return guideProfileRepository.findByMemberId(member.getId())
                    .map(guideProfile -> guideProfile.getId())
                    .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
        }
        return member.getId();
    }
}

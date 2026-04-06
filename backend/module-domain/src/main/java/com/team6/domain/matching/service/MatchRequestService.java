package com.team6.domain.matching.service;

import com.team6.domain.matching.dto.request.MatchRequestCreateRequest;
import com.team6.domain.matching.dto.response.MatchRequestCreateResponse;
import com.team6.domain.matching.dto.response.MatchRequestActionResponse;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MatchRequestService {

    private final MatchRequestRepository matchRequestRepository;

    // 매칭 요청 생성
    public MatchRequestCreateResponse createMatchRequest(Long guestId, MatchRequestCreateRequest request) {
        validateCreateRequest(guestId, request);

        MatchRequest matchRequest = MatchRequest.create(
                guestId,
                request.getGuideId(),
                request.getDestination(),
                request.getConcept(),
                request.getDesiredDate(),
                request.getDesiredBudget()
        );

        log.info("[MatchRequest] 매칭 요청 생성 — guestId={}, guideId={}", guestId, request.getGuideId());
        return MatchRequestCreateResponse.from(matchRequestRepository.save(matchRequest));
    }

    // 가이드의 매칭 요청 목록 조회
    @Transactional(readOnly = true)
    public List<MatchRequestCreateResponse> getGuideRequests(Long guideId) {
        return matchRequestRepository.findByGuideId(guideId).stream()
                .map(MatchRequestCreateResponse::from)
                .toList();
    }

    // 가이드 요청 거절
    public MatchRequestActionResponse rejectMatchRequest(Long guideId, Long requestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuideId().equals(guideId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.reject();
        log.info("[MatchRequest] 가이드 거절 — requestId={}, guideId={}", requestId, guideId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    // 가이드 제안 단계로 변경
    public MatchRequestActionResponse proposeMatchRequest(Long guideId, Long requestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuideId().equals(guideId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.propose();
        log.info("[MatchRequest] 가이드 제안 — requestId={}, guideId={}", requestId, guideId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    // 게스트 최종 수락
    public MatchRequestActionResponse acceptMatchRequest(Long guestId, Long requestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.accept();
        log.info("[MatchRequest] 게스트 수락 — requestId={}, guestId={}", requestId, guestId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    // F05-01 게스트 취소
    public MatchRequestActionResponse cancelByGuest(Long guestId, Long requestId, String reason) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.cancelByGuest(reason);
        log.info("[F05-01] 게스트 취소 완료 — requestId={}, guestId={}", requestId, guestId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    // F05-02 가이드 취소
    public MatchRequestActionResponse cancelByGuide(Long guideId, Long requestId, String reason) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuideId().equals(guideId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.cancelByGuide(reason);
        log.info("[F05-02] 가이드 취소 완료 — requestId={}, guideId={}", requestId, guideId);
        return MatchRequestActionResponse.from(matchRequest);
    }
    //그대로 return 하지 않고,
    //.from()이라는 정적 팩토리 메서드를 호출하여 D
    // DTO로 변환한 뒤 반환

    private void validateCreateRequest(Long guestId, MatchRequestCreateRequest request) {
        if (guestId.equals(request.getGuideId())) {
            throw new MatchingException(MatchingErrorCode.GUEST_GUIDE_SAME);
        }
    }
}
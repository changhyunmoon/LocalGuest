package com.team6.domain.matching.service;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.client.GuideScheduleSyncClient;
import com.team6.domain.matching.dto.request.MatchRequestCreateRequest;
import com.team6.domain.matching.dto.request.MatchRequestDeclineRequest;
import com.team6.domain.matching.dto.request.MatchRequestProposeRequest;
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

import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MatchRequestService {

    private final MatchRequestRepository matchRequestRepository;
    private final GuideScheduleSyncClient guideScheduleSyncClient;
    private final GuideProfileRepository guideProfileRepository;

    // 매칭 요청 생성
    public MatchRequestCreateResponse createMatchRequest(Long guestId, MatchRequestCreateRequest request) {
        Long guideMemberId = resolveGuideMemberId(request.getGuideId());
        validateCreateRequest(guestId, guideMemberId);

        String conceptSummary = resolveConceptSummary(request);
        MatchRequest matchRequest = MatchRequest.create(
                guestId,
                request.getGuideId(),
                request.getGuideScheduleId(),
                request.getDestination(),
                request.getConcept(),
                conceptSummary,
                request.getDesiredDate(),
                request.getDesiredBudget()
        );

        MatchRequest saved = matchRequestRepository.save(matchRequest);
        guideScheduleSyncClient.markPending(saved.getGuideId(), saved.getGuideScheduleId(), saved.getId(), guideMemberId);
        log.info("[F03-04] 매칭 요청 생성 — guestId={}, guideId={}", guestId, request.getGuideId());
        return MatchRequestCreateResponse.from(saved);
    }

    /** 게스트 본인 매칭 요청 전체 (마이페이지 일정·스크랩북 등) */
    public List<MatchRequestCreateResponse> getGuestRequests(Long guestId) {
        LocalDate today = LocalDate.now();
        return matchRequestRepository.findByGuestId(guestId).stream()
                .peek(request -> syncTourProgress(request, today))
                .map(MatchRequestCreateResponse::from)
                .toList();
    }

    // 가이드의 매칭 요청 목록 조회
    public List<MatchRequestCreateResponse> getGuideRequests(Long guideId) {
        LocalDate today = LocalDate.now();
        return matchRequestRepository.findByGuideId(guideId).stream()
                .peek(request -> syncTourProgress(request, today))
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
        Long guideMemberId = resolveGuideMemberId(matchRequest.getGuideId());
        guideScheduleSyncClient.cancelToAvailable(matchRequest.getGuideId(), matchRequest.getGuideScheduleId(), guideMemberId);
        log.info("[MatchRequest] 가이드 거절 — requestId={}, guideId={}", requestId, guideId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    // 가이드 제안 단계로 변경
    public MatchRequestActionResponse proposeMatchRequest(Long guideId, Long requestId, MatchRequestProposeRequest request) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuideId().equals(guideId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.applyProposal(request.getProposedSchedule(), request.getProposeMessage());
        log.info("[F03-05] 가이드 제시안 등록 — requestId={}, guideId={}", requestId, guideId);
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
        log.info("[F03-06] 게스트 최종 수락 — requestId={}, guestId={}", requestId, guestId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    // F03-06 게스트 최종 거절
    public MatchRequestActionResponse declineMatchRequest(Long guestId, Long requestId, MatchRequestDeclineRequest request) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.declineProposalByGuest(request.getReason());
        Long guideMemberId = resolveGuideMemberId(matchRequest.getGuideId());
        guideScheduleSyncClient.cancelToAvailable(matchRequest.getGuideId(), matchRequest.getGuideScheduleId(), guideMemberId);
        log.info("[F03-06] 게스트 최종 거절 — requestId={}, guestId={}", requestId, guestId);
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
        Long guideMemberId = resolveGuideMemberId(matchRequest.getGuideId());
        guideScheduleSyncClient.cancelToAvailable(matchRequest.getGuideId(), matchRequest.getGuideScheduleId(), guideMemberId);
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
        Long guideMemberId = resolveGuideMemberId(matchRequest.getGuideId());
        guideScheduleSyncClient.cancelToAvailable(matchRequest.getGuideId(), matchRequest.getGuideScheduleId(), guideMemberId);
        log.info("[F05-02] 가이드 취소 완료 — requestId={}, guideId={}", requestId, guideId);
        return MatchRequestActionResponse.from(matchRequest);
    }

    /**
     * 동일 이메일에 Guest/Guide 역할이 한 {@code member} 행에 공존할 수 있다.
     * 자기 자신의 가이드 프로필에게 매칭 요청하는 경우에만 {@code guestId == guideMemberId}가 되므로 이를 차단한다.
     */
    private void validateCreateRequest(Long guestId, Long guideMemberId) {
        if (guestId.equals(guideMemberId)) {
            throw new MatchingException(MatchingErrorCode.GUEST_GUIDE_SAME);
        }
    }

    private String resolveConceptSummary(MatchRequestCreateRequest request) {
        if (request.getConceptSummary() != null && !request.getConceptSummary().isBlank()) {
            return request.getConceptSummary().trim();
        }

        String destination = safeTrim(request.getDestination());
        String concept = safeTrim(request.getConcept());
        String date = request.getDesiredDate() == null ? null : request.getDesiredDate().toString();
        String budget = formatBudget(request.getDesiredBudget());

        StringBuilder sb = new StringBuilder();
        if (destination != null) {
            sb.append('[').append(destination).append("] ");
        }
        if (date != null) {
            sb.append(date);
        }
        if (budget != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("예산 ").append(budget);
        }
        if (concept != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("컨셉: ").append(concept);
        }

        String summary = sb.toString().trim();
        if (summary.isBlank()) {
            return null;
        }
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isBlank() ? null : t;
    }

    private String formatBudget(Integer desiredBudget) {
        if (desiredBudget == null) {
            return null;
        }
        if (desiredBudget < 0) {
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.KOREA);
        return nf.format(desiredBudget) + "원";
    }

    /**
     * 스케줄 동기화 등에 넘기는 actor — 가이드 측 {@code member.id}.
     * {@code guideProfileId}는 {@code guide_profiles.id}; 프로필당 member 1명이므로 항상 단일 member PK로 귀결된다.
     */
    private Long resolveGuideMemberId(Long guideProfileId) {
        return guideProfileRepository.findById(guideProfileId)
                .map(guideProfile -> guideProfile.getMemberId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }

    private void syncTourProgress(MatchRequest request, LocalDate today) {
        request.markInProgressIfTourDay(today);
        request.markCompletedIfTourEnded(today);
    }
}

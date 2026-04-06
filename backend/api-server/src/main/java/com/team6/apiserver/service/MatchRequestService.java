package com.team6.apiserver.service;

import com.team6.apiserver.dto.request.MatchRequestCreateRequest;
import com.team6.apiserver.dto.response.MatchRequestSummaryResponse;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchRequestService {

    private final MatchRequestRepository matchRequestRepository;

    public MatchRequest createMatchRequest(Long guestId, MatchRequestCreateRequest request) {
        validateCreateRequest(guestId, request);

        MatchRequest matchRequest = MatchRequest.create(
                guestId,
                request.getGuideId(),
                request.getDestination(),
                request.getConcept(),
                request.getDesiredDate(),
                request.getDesiredBudget()
        );

        return matchRequestRepository.save(matchRequest);
    }

    @Transactional(readOnly = true)
    public List<MatchRequestSummaryResponse> getGuideRequests(Long guideId) {
        return matchRequestRepository.findByGuideId(guideId).stream()
                .map(MatchRequestSummaryResponse::from)
                .toList();
    }

    public MatchRequest rejectMatchRequest(Long guideId, Long requestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuideId().equals(guideId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.reject();
        return matchRequest;
    }

    public MatchRequest proposeMatchRequest(Long guideId, Long requestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuideId().equals(guideId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.propose();
        return matchRequest;
    }

    public MatchRequest acceptMatchRequest(Long guestId, Long requestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        matchRequest.accept();
        return matchRequest;
    }

    private void validateCreateRequest(Long guestId, MatchRequestCreateRequest request) {
        if (request.getGuideId() == null) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND);
        }

        if (request.getDestination() == null || request.getDestination().isBlank()) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND);
        }

        if (guestId.equals(request.getGuideId())) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
    }
}
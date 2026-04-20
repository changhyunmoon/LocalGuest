package com.team6.domain.matching.service;

import com.team6.domain.matching.dto.request.TourExtensionSelectRequest;
import com.team6.domain.matching.dto.response.TourExtensionResponseDto;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.TourExtension;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.TourExtensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TourExtensionService {

    private final TourExtensionRepository tourExtensionRepository;
    private final MatchRequestRepository matchRequestRepository;

    @Transactional(readOnly = true)
    public TourExtensionResponseDto getByRequestId(Long requestId, Long memberId) {
        TourExtension extension = tourExtensionRepository.findByMatchRequest_Id(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND));

        boolean isGuest = extension.getGuestId().equals(memberId);
        boolean isGuide = extension.getMatchRequest().getGuideId().equals(memberId);
        if (!isGuest && !isGuide) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        return TourExtensionResponseDto.from(extension);
    }

    // 게스트 연장 여부 선택 (F05-03)
    public TourExtensionResponseDto selectByGuest(Long guestId, Long requestId, TourExtensionSelectRequest request) {
        TourExtension extension = tourExtensionRepository.findByMatchRequest_Id(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND));

        if (!extension.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        if (LocalDateTime.now().isAfter(extension.getDeadlineAt())) {
            throw new MatchingException(MatchingErrorCode.TOUR_EXTENSION_DEADLINE_EXCEEDED);
        }

        // 현재 모델에서 "선택 완료" 상태를 저장하기 위해
        // 연장 선택(true)은 PAID, 미연장 선택(false)은 REJECTED로 기록한다.
        if (Boolean.TRUE.equals(request.getExtend())) {
            extension.completePayByGuestSelection();
            log.info("[F05-03] 게스트 하루 연장 선택 완료 — requestId={}, guestId={}", requestId, guestId);
        } else {
            extension.rejectByGuest();
            log.info("[F05-03] 게스트 미연장 선택 완료 — requestId={}, guestId={}", requestId, guestId);
        }

        return TourExtensionResponseDto.from(extension);
    }

    // 당일 21:00 알림 + 선택 오픈 레코드 생성
    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
    public void openExtensionSelectionAt2100() {
        LocalDate today = LocalDate.now();
        List<MatchRequestStatus> targetStatuses = List.of(
                MatchRequestStatus.ACCEPTED,
                MatchRequestStatus.PAID,
                MatchRequestStatus.IN_PROGRESS
        );

        List<MatchRequest> targets =
                matchRequestRepository.findByDesiredDateAndStatusIn(today, targetStatuses);

        int createdCount = 0;
        for (MatchRequest matchRequest : targets) {
            boolean exists = tourExtensionRepository.findByMatchRequest_Id(matchRequest.getId()).isPresent();
            if (exists) {
                continue;
            }

            TourExtension extension = TourExtension.builder()
                    .matchRequest(matchRequest)
                    .guestId(matchRequest.getGuestId())
                    .extendedDate(matchRequest.getDesiredDate().plusDays(1))
                    .deadlineAt(today.atTime(23, 59, 59))
                    .build();
            tourExtensionRepository.save(extension);
            createdCount++;
        }

        log.info("[F05-03] 당일 21:00 연장 선택 오픈 — targetCount={}, createdCount={}", targets.size(), createdCount);
    }

    // 마감(23:59:59) 후 미선택 자동 미연장 처리
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void autoCancelUnselectedExtensions() {
        List<TourExtension> expired = tourExtensionRepository.findByStatusAndDeadlineAtBefore(
                TourExtensionStatus.REQUESTED,
                LocalDateTime.now()
        );

        for (TourExtension extension : expired) {
            extension.autoCancel();
        }

        if (!expired.isEmpty()) {
            log.info("[F05-03] 연장 미선택 자동 미연장 처리 — count={}", expired.size());
        }
    }
}

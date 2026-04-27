package com.team6.domain.matching.service;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.guide.repository.GuideScheduleRepository;
import com.team6.domain.matching.dto.request.TourExtensionSelectRequest;
import com.team6.domain.matching.dto.response.TourExtensionResponseDto;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.TourExtension;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.PaymentRepository;
import com.team6.domain.matching.repository.TourExtensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TourExtensionService {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DAY_PACKAGE_HOURS = 8;

    private final TourExtensionRepository tourExtensionRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final GuideScheduleRepository guideScheduleRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final PaymentRepository paymentRepository;

    public TourExtensionResponseDto getByRequestId(Long requestId, Long memberId) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));
        boolean isGuest = matchRequest.getGuestId().equals(memberId);
        boolean isGuide = matchRequest.getGuideId().equals(memberId);
        if (!isGuest && !isGuide) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        TourExtension extension = tourExtensionRepository.findByMatchRequest_Id(requestId)
                .orElseGet(() -> createOnDemandTodayExtension(matchRequest));
        sanitizeLegacyOrUnavailableExtension(extension, matchRequest);

        return TourExtensionResponseDto.from(extension);
    }

    private TourExtension createOnDemandTodayExtension(MatchRequest matchRequest) {
        LocalDateTime now = nowSeoul();
        LocalDate today = now.toLocalDate();
        List<MatchRequestStatus> targetStatuses = List.of(
                MatchRequestStatus.ACCEPTED,
                MatchRequestStatus.PAID,
                MatchRequestStatus.IN_PROGRESS,
                MatchRequestStatus.COMPLETED
        );
        boolean inOpenWindow = now.getHour() >= 21 && now.getHour() <= 23;
        if (!inOpenWindow
                || !today.equals(matchRequest.getDesiredDate())
                || !targetStatuses.contains(matchRequest.getStatus())) {
            throw new MatchingException(MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND);
        }
        NextDayAvailability nextDayAvailability = getNextDayAvailability(matchRequest);
        if (nextDayAvailability != NextDayAvailability.EXTENDABLE) {
            throw new MatchingException(toGuideUnavailableErrorCode(nextDayAvailability));
        }

        TourExtension extension = TourExtension.builder()
                .matchRequest(matchRequest)
                .guestId(matchRequest.getGuestId())
                .extendedDate(matchRequest.getDesiredDate().plusDays(1))
                .extendedPrice(resolveDayPackagePrice(matchRequest.getGuideId()))
                .deadlineAt(today.atTime(23, 59, 59))
                .build();
        TourExtension saved = tourExtensionRepository.save(extension);
        log.info("[F05-03] 온디맨드 연장 선택 오픈 생성 — requestId={}, guestId={}",
                matchRequest.getId(), matchRequest.getGuestId());
        return saved;
    }

    // 게스트 연장 여부 선택 (F05-03)
    public TourExtensionResponseDto selectByGuest(Long guestId, Long requestId, TourExtensionSelectRequest request) {
        TourExtension extension = tourExtensionRepository.findByMatchRequest_Id(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND));

        if (!extension.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        if (nowSeoul().isAfter(extension.getDeadlineAt())) {
            throw new MatchingException(MatchingErrorCode.TOUR_EXTENSION_DEADLINE_EXCEEDED);
        }

        // 연장 선택(true): 결제 대기 상태(GUIDE_APPROVED)로 전환한다.
        // 실제 PAID 전환은 EXTENSION 결제 승인 시 처리한다.
        if (Boolean.TRUE.equals(request.getExtend())) {
            if (extension.getStatus() != TourExtensionStatus.REQUESTED
                    && extension.getStatus() != TourExtensionStatus.GUIDE_APPROVED) {
                throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
            }
            if (!isNextDayExtendable(extension.getMatchRequest())) {
                throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
            }
            extension.approveByGuide(resolveDayPackagePrice(extension.getMatchRequest().getGuideId()));
            log.info("[F05-03] 게스트 하루 연장 선택(결제 대기) — requestId={}, guestId={}", requestId, guestId);
        } else {
            extension.rejectByGuest();
            log.info("[F05-03] 게스트 미연장 선택 완료 — requestId={}, guestId={}", requestId, guestId);
        }

        return TourExtensionResponseDto.from(extension);
    }

    // 당일 21:00~23:59 사이에 연장 선택 오픈 레코드를 보정 생성한다.
    // (서버 재시작/배포 타이밍으로 21:00 단발 배치가 누락돼도 매분 복구되도록)
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void openExtensionSelectionWindow() {
        LocalDateTime now = nowSeoul();
        if (now.getHour() < 21 || now.getHour() > 23) {
            return;
        }
        LocalDate today = now.toLocalDate();
        List<MatchRequestStatus> targetStatuses = List.of(
                MatchRequestStatus.ACCEPTED,
                MatchRequestStatus.PAID,
                MatchRequestStatus.IN_PROGRESS,
                MatchRequestStatus.COMPLETED
        );

        List<MatchRequest> targets =
                matchRequestRepository.findByDesiredDateAndStatusIn(today, targetStatuses);

        int createdCount = 0;
        for (MatchRequest matchRequest : targets) {
            boolean exists = tourExtensionRepository.findByMatchRequest_Id(matchRequest.getId()).isPresent();
            if (exists) {
                continue;
            }
            if (!isNextDayExtendable(matchRequest)) {
                continue;
            }

            Integer dayPackagePrice;
            try {
                dayPackagePrice = resolveDayPackagePrice(matchRequest.getGuideId());
            } catch (MatchingException e) {
                continue;
            }

            TourExtension extension = TourExtension.builder()
                    .matchRequest(matchRequest)
                    .guestId(matchRequest.getGuestId())
                    .extendedDate(matchRequest.getDesiredDate().plusDays(1))
                    .extendedPrice(dayPackagePrice)
                    .deadlineAt(today.atTime(23, 59, 59))
                    .build();
            tourExtensionRepository.save(extension);
            createdCount++;
        }

        if (createdCount > 0) {
            log.info("[F05-03] 당일 21:00~23:59 연장 선택 오픈 보정 — targetCount={}, createdCount={}", targets.size(), createdCount);
        }
    }

    // 마감(23:59:59) 후 미선택 자동 미연장 처리
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void autoCancelUnselectedExtensions() {
        List<TourExtension> expired = tourExtensionRepository.findByStatusAndDeadlineAtBefore(
                TourExtensionStatus.REQUESTED,
                nowSeoul()
        );

        for (TourExtension extension : expired) {
            extension.autoCancel();
        }

        if (!expired.isEmpty()) {
            log.info("[F05-03] 연장 미선택 자동 미연장 처리 — count={}", expired.size());
        }
    }

    private LocalDateTime nowSeoul() {
        return LocalDateTime.now(SEOUL_ZONE);
    }

    private boolean isNextDayExtendable(MatchRequest matchRequest) {
        return getNextDayAvailability(matchRequest) == NextDayAvailability.EXTENDABLE;
    }

    private NextDayAvailability getNextDayAvailability(MatchRequest matchRequest) {
        LocalDate nextDay = matchRequest.getDesiredDate().plusDays(1);
        List<GuideSchedule> nextDaySchedules =
                guideScheduleRepository.findByGuideProfile_IdAndAvailableDate(matchRequest.getGuideId(), nextDay);
        boolean hasBlocked = nextDaySchedules.stream()
                .map(GuideSchedule::getStatus)
                .anyMatch(status -> status == GuideScheduleStatus.BLOCKED);
        if (hasBlocked) {
            return NextDayAvailability.BLOCKED;
        }
        boolean hasBooked = nextDaySchedules.stream()
                .map(GuideSchedule::getStatus)
                .anyMatch(status -> status == GuideScheduleStatus.PENDING
                        || status == GuideScheduleStatus.BOOKED);
        if (hasBooked) {
            return NextDayAvailability.BOOKED;
        }
        return NextDayAvailability.EXTENDABLE;
    }

    private Integer resolveDayPackagePrice(Long guideProfileId) {
        GuideProfile profile = guideProfileRepository.findById(guideProfileId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.INVALID_REQUEST));
        BigDecimal hourly = profile.getPricePerHour();
        if (hourly == null || hourly.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }
        return hourly.multiply(BigDecimal.valueOf(DAY_PACKAGE_HOURS))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private void sanitizeLegacyOrUnavailableExtension(TourExtension extension, MatchRequest matchRequest) {
        NextDayAvailability nextDayAvailability = getNextDayAvailability(matchRequest);
        if ((extension.getStatus() == TourExtensionStatus.REQUESTED || extension.getStatus() == TourExtensionStatus.GUIDE_APPROVED)
                && nextDayAvailability != NextDayAvailability.EXTENDABLE) {
            extension.autoCancel();
            throw new MatchingException(toGuideUnavailableErrorCode(nextDayAvailability));
        }

        // 과거 로직 잔재: 결제 없이 상태만 PAID가 된 레코드 정리
        if (extension.getStatus() == TourExtensionStatus.PAID && !hasCompletedExtensionPayment(matchRequest.getId())) {
            extension.autoCancel();
            throw new MatchingException(MatchingErrorCode.TOUR_EXTENSION_ALREADY_DECIDED);
        }
    }

    private boolean hasCompletedExtensionPayment(Long requestId) {
        return paymentRepository.findByMatchRequest_IdAndPaymentType(requestId, "EXTENSION")
                .map(payment -> payment.getStatus() == PaymentStatus.COMPLETED)
                .orElse(false);
    }

    private MatchingErrorCode toGuideUnavailableErrorCode(NextDayAvailability availability) {
        return switch (availability) {
            case BLOCKED -> MatchingErrorCode.TOUR_EXTENSION_GUIDE_NEXT_DAY_BLOCKED;
            case BOOKED -> MatchingErrorCode.TOUR_EXTENSION_GUIDE_NEXT_DAY_BOOKED;
            default -> MatchingErrorCode.TOUR_EXTENSION_GUIDE_UNAVAILABLE_NEXT_DAY;
        };
    }

    private enum NextDayAvailability {
        EXTENDABLE,
        BOOKED,
        BLOCKED
    }
}

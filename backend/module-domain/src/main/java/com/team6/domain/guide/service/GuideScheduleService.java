package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideScheduleRequest;
import com.team6.domain.guide.dto.request.SubmitGuideScheduleFormRequest;
import com.team6.domain.guide.dto.request.UpdateGuideScheduleRequest;
import com.team6.domain.guide.dto.response.GuideScheduleFormResponse;
import com.team6.domain.guide.dto.response.GuideScheduleResponse;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.guide.repository.GuideScheduleRepository;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 가이드 스케줄 서비스 (F06-04)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GuideScheduleService {

    private final GuideScheduleRepository guideScheduleRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final MatchRequestRepository matchRequestRepository;

    private static final EnumSet<MatchRequestStatus> ACTIVE_MATCH_STATUSES =
            EnumSet.of(MatchRequestStatus.PENDING, MatchRequestStatus.ACCEPTED, MatchRequestStatus.PAID, MatchRequestStatus.IN_PROGRESS);
    private static final Pattern NIGHTS_DAYS = Pattern.compile("(\\d{1,2})\\s*박\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAYS_ONLY = Pattern.compile("(\\d{1,2})\\s*일\\s*일정");

    // 스케줄 등록 (F06-04)
    @Transactional
    public GuideScheduleResponse addSchedule(Long guideId, CreateGuideScheduleRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        GuideProfile profile = getVerifiedProfile(guideId, userId);

        // 시작 시간이 종료 시간보다 같거나 늦으면 예외 발생
        validateTimeRange(request.getStartTime(), request.getEndTime());

        // 동일 날짜·시간대 스케줄 겹침 방지 (UniqueConstraint 기준: date + start_time + end_time)
        if (guideScheduleRepository.existsOverlappingSchedule(
                guideId, request.getAvailableDate(), request.getStartTime(), request.getEndTime())) {
            throw new GuideException(GuideErrorCode.SCHEDULE_ALREADY_BOOKED);
        }

        // 스케줄 엔티티 생성 및 저장
        GuideSchedule schedule = GuideSchedule.builder()
                .guideProfile(profile)
                .availableDate(request.getAvailableDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();

        return GuideScheduleResponse.from(guideScheduleRepository.save(schedule));
    }

    // 스케줄 수정 (F06-04)
    @Transactional
    public GuideScheduleResponse updateSchedule(Long scheduleId, Long guideId, UpdateGuideScheduleRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 시작 시간이 종료 시간보다 같거나 늦으면 예외 발생
        validateTimeRange(request.getStartTime(), request.getEndTime());

        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // 예약 확정된 스케줄은 수정 불가
        if (schedule.getStatus() == GuideScheduleStatus.BOOKED) {
            throw new GuideException(GuideErrorCode.SCHEDULE_ALREADY_BOOKED);
        }

        // 스케줄 수정 — status가 null이면 기존 상태 유지
        GuideScheduleStatus newStatus = request.getStatus() != null
                ? request.getStatus()
                : schedule.getStatus();

        schedule.update(
                request.getAvailableDate(),
                request.getStartTime(),
                request.getEndTime(),
                newStatus
        );

        return GuideScheduleResponse.from(schedule);
    }

    // 스케줄 삭제 (F06-04)
    @Transactional
    public void deleteSchedule(Long scheduleId, Long guideId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // 예약됐거나 대기 중인 스케줄은 삭제 불가
        if (schedule.getStatus() == GuideScheduleStatus.BOOKED ||
                schedule.getStatus() == GuideScheduleStatus.PENDING) {
            throw new GuideException(GuideErrorCode.SCHEDULE_ALREADY_BOOKED);
        }

        guideScheduleRepository.delete(schedule);
    }

    // 스케줄 목록 조회 — 날짜 오름차순 (F06-04)
    @Transactional(readOnly = true)
    public List<GuideScheduleResponse> getSchedules(Long guideId) {
        List<GuideScheduleResponse> base = guideScheduleRepository.findByGuideProfile_IdOrderByAvailableDateAsc(guideId).stream()
                .map(GuideScheduleResponse::from)
                .toList();

        // 연속 일정(예: 1박2일/2박3일) 요청이 PENDING~IN_PROGRESS 상태일 때,
        // 다른 사용자의 달력에서도 해당 기간이 예약 불가로 보이도록 "가상 PENDING" 엔트리를 덧붙인다.
        Map<LocalDate, Long> reservedDateToRequestId = new LinkedHashMap<>();
        for (MatchRequest mr : matchRequestRepository.findByGuideId(guideId)) {
            if (mr == null || mr.getStatus() == null || !ACTIVE_MATCH_STATUSES.contains(mr.getStatus())) {
                continue;
            }
            LocalDate start = mr.getDesiredDate();
            if (start == null) {
                continue;
            }
            int days = inferDurationDays(mr);
            if (days <= 1) {
                continue;
            }
            for (int i = 0; i < days; i++) {
                reservedDateToRequestId.putIfAbsent(start.plusDays(i), mr.getId());
            }
        }

        if (reservedDateToRequestId.isEmpty()) {
            return base;
        }

        ArrayList<GuideScheduleResponse> out = new ArrayList<>(base);
        for (Map.Entry<LocalDate, Long> e : reservedDateToRequestId.entrySet()) {
            LocalDate d = e.getKey();
            if (d == null) {
                continue;
            }
            boolean alreadyReserved =
                    base.stream().anyMatch(r ->
                            d.equals(r.getAvailableDate())
                                    && (r.getStatus() == GuideScheduleStatus.PENDING || r.getStatus() == GuideScheduleStatus.BOOKED)
                    );
            if (alreadyReserved) {
                continue;
            }
            out.add(GuideScheduleResponse.builder()
                    .scheduleId(null)
                    .guideId(guideId)
                    .availableDate(d)
                    .startTime(null)
                    .endTime(null)
                    .status(GuideScheduleStatus.PENDING)
                    .matchRequestId(e.getValue())
                    .isPaid(false)
                    .hasCourse(false)
                    .createdAt(null)
                    .updatedAt(null)
                    .build());
        }

        out.sort(Comparator
                .comparing(GuideScheduleResponse::getAvailableDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GuideScheduleResponse::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private static int inferDurationDays(MatchRequest mr) {
        String blob = ((mr.getConceptSummary() == null ? "" : mr.getConceptSummary()) + " " + (mr.getConcept() == null ? "" : mr.getConcept())).trim();
        if (blob.isBlank()) {
            return 1;
        }
        var m1 = NIGHTS_DAYS.matcher(blob);
        if (m1.find()) {
            int days = safeParseInt(m1.group(2));
            return clampDays(days);
        }
        var m2 = DAYS_ONLY.matcher(blob);
        if (m2.find()) {
            int days = safeParseInt(m2.group(1));
            return clampDays(days);
        }
        return 1;
    }

    private static int safeParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int clampDays(int days) {
        if (days <= 1) {
            return 1;
        }
        return Math.min(14, days);
    }

    // 스케줄 상태 변경 (AVAILABLE ↔ BLOCKED) (F06-04)
    @Transactional
    public GuideScheduleResponse changeStatus(Long scheduleId, Long guideId, GuideScheduleStatus status, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // AVAILABLE ↔ BLOCKED 전환만 허용 (BOOKED/PENDING은 직접 설정 불가)
        if (status != GuideScheduleStatus.AVAILABLE && status != GuideScheduleStatus.BLOCKED) {
            throw new GuideException(GuideErrorCode.SCHEDULE_STATUS_INVALID);
        }
        // 상태 변경
        schedule.changeStatus(status);

        return GuideScheduleResponse.from(schedule);
    }

    // PENDING 상태 스케줄 목록 조회 — 가이드가 수락/거절할 대기 스케줄 확인 (F06-04)
    @Transactional(readOnly = true)
    public List<GuideScheduleResponse> getPendingSchedules(Long guideId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // PENDING 상태 스케줄만 조회
        return guideScheduleRepository.findByGuideProfile_IdAndStatus(guideId, GuideScheduleStatus.PENDING).stream()
                .map(GuideScheduleResponse::from)
                .toList();
    }

    // 스케줄 수락 — PENDING → BOOKED (F06-04)
    @Transactional
    public GuideScheduleFormResponse acceptSchedule(Long scheduleId, Long guideId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // PENDING 상태인지 확인 (수락은 대기 중 스케줄에만 가능)
        if (schedule.getStatus() != GuideScheduleStatus.PENDING) {
            throw new GuideException(GuideErrorCode.SCHEDULE_NOT_PENDING);
        }

        // 상태 변경: PENDING → BOOKED
        schedule.changeStatus(GuideScheduleStatus.BOOKED);

        // 수락 후 가이드가 작성할 양식 반환
        return GuideScheduleFormResponse.from(schedule);
    }

    // 스케줄 거절 — PENDING → AVAILABLE 복구 (F06-04)
    @Transactional
    public GuideScheduleResponse rejectSchedule(Long scheduleId, Long guideId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // PENDING 상태인지 확인 (거절은 대기 중 스케줄에만 가능)
        if (schedule.getStatus() != GuideScheduleStatus.PENDING) {
            throw new GuideException(GuideErrorCode.SCHEDULE_NOT_PENDING);
        }

        // 상태 복구: PENDING → AVAILABLE
        schedule.changeStatus(GuideScheduleStatus.AVAILABLE);

        return GuideScheduleResponse.from(schedule);
    }

    // 스케줄 양식 조회 — isPaid=true면 courseDetail 공개, false면 null 마스킹 (F06-04)
    // 가이드·게스트 모두 호출 가능 (본인 확인 없음)
    // TODO: 추후 보안 강화 필요
    // 현재 인증된 사용자라면 누구나 호출 가능
    // 개선 시 가이드 본인 또는 매칭된 게스트만 허용하도록 변경 필요
    @Transactional(readOnly = true)
    public GuideScheduleFormResponse getScheduleForm(Long scheduleId, Long guideId, boolean isGuideCaller) {
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);
        if (isGuideCaller) {
            return GuideScheduleFormResponse.from(schedule, true);
        }

        MatchRequestStatus st = resolveMatchStatus(schedule.getMatchRequestId());
        if (st == MatchRequestStatus.COMPLETED) {
            return GuideScheduleFormResponse.from(schedule, true);
        }
        if (st == MatchRequestStatus.PAID || st == MatchRequestStatus.IN_PROGRESS) {
            String masked = maskCourseDetail(schedule.getCourseDetail());
            return GuideScheduleFormResponse.from(schedule, true, masked);
        }
        // 결제 전(ACCEPTED) 등: 상세 코스는 숨김
        return GuideScheduleFormResponse.from(schedule, false);
    }

    private MatchRequestStatus resolveMatchStatus(Long matchRequestId) {
        if (matchRequestId == null) return null;
        return matchRequestRepository.findById(matchRequestId)
                .map(MatchRequest::getStatus)
                .orElse(null);
    }

    /**
     * courseDetail 포맷(줄 단위): "n. name | time | desc" (+ 이후 확장 필드가 있어도 보존)
     * 결제 후(완료 전)에는 name만 마스킹한다.
     */
    private String maskCourseDetail(String courseDetail) {
        if (courseDetail == null || courseDetail.isBlank()) return courseDetail;
        String[] lines = courseDetail.split("\\R");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String trimmed = line.trim();
            // "1. ..." prefix 제거 후 '|' 기준으로 파싱
            String noNum = trimmed.replaceFirst("^\\d+\\.\\s*", "");
            String[] parts = noNum.split("\\|", -1);
            if (parts.length >= 1) {
                parts[0] = "로컬 스팟";
            }
            String rebuilt = String.join(" | ", java.util.Arrays.stream(parts).map(String::trim).toArray(String[]::new));
            // 원래 번호가 있으면 유지
            String prefix = trimmed.matches("^\\d+\\..*") ? trimmed.replaceFirst("^(\\d+\\.).*$", "$1 ") : "";
            if (!prefix.isEmpty()) out.append(prefix);
            out.append(rebuilt);
            out.append('\n');
        }
        return out.toString().trim();
    }

    // 수락 후 여행 계획 양식 저장 — BOOKED 상태 스케줄에만 가능 (F06-04)
    @Transactional
    public GuideScheduleFormResponse submitForm(Long scheduleId, Long guideId, SubmitGuideScheduleFormRequest request, Long userId) {
        getVerifiedProfile(guideId, userId);

        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        if (schedule.getStatus() != GuideScheduleStatus.BOOKED) {
            throw new GuideException(GuideErrorCode.SCHEDULE_FORM_NOT_BOOKED);
        }

        schedule.submitForm(request.getMeetingPoint(), request.getGuideMessage(), request.getCourseDetail());

        return GuideScheduleFormResponse.from(schedule);
    }

    // [matching 연동] AVAILABLE → PENDING 전환 — matching 도메인이 매칭 요청 시 호출
    @Transactional
    public GuideScheduleResponse markAsPending(Long scheduleId, Long guideId, Long matchRequestId, Long actorMemberId) {
        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // AVAILABLE 상태인지 확인 (PENDING 전환은 예약 가능 상태에서만 허용)
        if (schedule.getStatus() != GuideScheduleStatus.AVAILABLE) {
            throw new GuideException(GuideErrorCode.SCHEDULE_NOT_AVAILABLE);
        }

        // 상태 변경: AVAILABLE → PENDING
        schedule.changeStatus(GuideScheduleStatus.PENDING);

        // matchRequestId 연결 — matching 도메인 크로스 참조 저장
        schedule.linkMatchRequest(matchRequestId);
        log.info("[F03-04] 스케줄 PENDING 동기화 — guideId={}, scheduleId={}, matchRequestId={}, actorId={}",
                guideId, scheduleId, matchRequestId, actorMemberId);

        return GuideScheduleResponse.from(schedule);
    }

    // [matching 연동] PENDING → BOOKED 전환 — matching 도메인이 최종 확정 시 호출
    @Transactional
    public GuideScheduleResponse markAsBooked(Long scheduleId, Long guideId, Long actorMemberId) {
        // 스케줄 조회
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        // PENDING 상태인지 확인 (BOOKED 전환은 대기 중 스케줄에만 허용)
        if (schedule.getStatus() != GuideScheduleStatus.PENDING) {
            throw new GuideException(GuideErrorCode.SCHEDULE_NOT_PENDING);
        }

        // 상태 변경: PENDING → BOOKED
        schedule.changeStatus(GuideScheduleStatus.BOOKED);
        log.info("[F03-05] 스케줄 BOOKED 동기화 — guideId={}, scheduleId={}, actorId={}",
                guideId, scheduleId, actorMemberId);

        return GuideScheduleResponse.from(schedule);
    }

    // [matching 연동] 결제 완료 확정 — PENDING이면 BOOKED로 자동 전환 후 isPaid=true 처리
    @Transactional
    public GuideScheduleResponse markAsPaid(Long scheduleId, Long guideId, Long actorMemberId) {
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        if (schedule.getStatus() == GuideScheduleStatus.PENDING) {
            schedule.changeStatus(GuideScheduleStatus.BOOKED);
        } else if (schedule.getStatus() != GuideScheduleStatus.BOOKED) {
            throw new GuideException(GuideErrorCode.SCHEDULE_NOT_BOOKED);
        }

        schedule.markAsPaid();
        log.info("[F03-06] 스케줄 결제확정 동기화 — guideId={}, scheduleId={}, actorId={}",
                guideId, scheduleId, actorMemberId);
        return GuideScheduleResponse.from(schedule);
    }

    // [matching 연동] PENDING 또는 BOOKED → AVAILABLE — 매칭 거절·취소 시 matching 도메인이 호출 (F03/F05)
    @Transactional
    public GuideScheduleResponse cancelToAvailable(Long scheduleId, Long guideId, Long actorMemberId) {
        GuideSchedule schedule = getVerifiedSchedule(scheduleId, guideId);

        if (schedule.getStatus() != GuideScheduleStatus.PENDING
                && schedule.getStatus() != GuideScheduleStatus.BOOKED) {
            throw new GuideException(GuideErrorCode.SCHEDULE_MATCH_RELEASE_INVALID);
        }

        schedule.releaseMatchToAvailable();
        log.info("[F05-01][F05-02] 스케줄 AVAILABLE 복구 동기화 — guideId={}, scheduleId={}, actorId={}",
                guideId, scheduleId, actorMemberId);
        return GuideScheduleResponse.from(schedule);
    }

    // BLOCKED 상태 스케줄 목록 조회 — 게스트 달력 조회용 (인증 불필요)
    @Transactional(readOnly = true)
    public List<GuideScheduleResponse> getBlockedDates(Long guideId) {
        return guideScheduleRepository.findByGuideProfile_IdAndStatus(guideId, GuideScheduleStatus.BLOCKED).stream()
                .map(GuideScheduleResponse::from)
                .toList();
    }

    // 날짜 단위 BLOCKED 등록 — 이미 BLOCKED면 스킵, AVAILABLE이면 상태 변경, 없으면 신규 생성
    @Transactional
    public GuideScheduleResponse blockDate(Long guideId, LocalDate date, Long userId) {
        GuideProfile profile = getVerifiedProfile(guideId, userId);

        List<GuideSchedule> existing = guideScheduleRepository.findByGuideProfile_IdAndAvailableDate(guideId, date);

        // 이미 BLOCKED인 날짜면 스킵 (멱등)
        boolean alreadyBlocked = existing.stream()
                .anyMatch(s -> s.getStatus() == GuideScheduleStatus.BLOCKED);
        if (alreadyBlocked) {
            GuideSchedule blocked = existing.stream()
                    .filter(s -> s.getStatus() == GuideScheduleStatus.BLOCKED)
                    .findFirst()
                    .orElseThrow();
            return GuideScheduleResponse.from(blocked);
        }

        // AVAILABLE 스케줄이 있으면 상태 변경
        if (!existing.isEmpty()) {
            GuideSchedule target = existing.get(0);
            target.changeStatus(GuideScheduleStatus.BLOCKED);
            return GuideScheduleResponse.from(target);
        }

        // 등록된 스케줄 없음 → 종일 BLOCKED 레코드 신규 생성 (00:00 – 23:59 sentinel)
        GuideSchedule block = GuideSchedule.builder()
                .guideProfile(profile)
                .availableDate(date)
                .startTime(LocalTime.of(0, 0))
                .endTime(LocalTime.of(23, 59))
                .status(GuideScheduleStatus.BLOCKED)
                .build();
        return GuideScheduleResponse.from(guideScheduleRepository.save(block));
    }

    // 날짜 단위 BLOCKED 해제 — BLOCKED 행 삭제 → 달력은 '설정 없음(중립)'으로 돌아감 (자동 예약 가능 아님)
    @Transactional
    public void unblockDate(Long guideId, LocalDate date, Long userId) {
        getVerifiedProfile(guideId, userId);

        List<GuideSchedule> blocked = guideScheduleRepository
                .findByGuideProfile_IdAndAvailableDate(guideId, date)
                .stream()
                .filter(s -> s.getStatus() == GuideScheduleStatus.BLOCKED)
                .toList();

        blocked.forEach(guideScheduleRepository::delete);
    }

    // 시작 시간 < 종료 시간 검증 공통 메서드 — DB의 CHECK 제약과 이중 보호
    private void validateTimeRange(java.time.LocalTime startTime, java.time.LocalTime endTime) {
        // startTime이 endTime과 같거나 이후인 경우 유효하지 않은 시간 범위로 판단
        if (!startTime.isBefore(endTime)) {
            throw new GuideException(GuideErrorCode.SCHEDULE_INVALID_TIME);
        }
    }

    // 가이드 프로필 조회 + 본인 여부 확인 공통 메서드
    private GuideProfile getVerifiedProfile(Long guideId, Long userId) {
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        if (!profile.getMemberId().equals(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        return profile;
    }

    // 스케줄 조회 + 해당 가이드 소유 여부 확인 공통 메서드
    private GuideSchedule getVerifiedSchedule(Long scheduleId, Long guideId) {
        GuideSchedule schedule = guideScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.SCHEDULE_NOT_FOUND));

        // 해당 가이드의 스케줄인지 확인
        if (!schedule.getGuideProfile().getId().equals(guideId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        return schedule;
    }
}

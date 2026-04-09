package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideScheduleRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가이드 스케줄 서비스 (F06-04)
 */
@Service
@RequiredArgsConstructor
public class GuideScheduleService {

    private final GuideScheduleRepository guideScheduleRepository;
    private final GuideProfileRepository guideProfileRepository;

    // 스케줄 등록 (F06-04)
    @Transactional
    public GuideScheduleResponse addSchedule(Long guideId, CreateGuideScheduleRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        GuideProfile profile = getVerifiedProfile(guideId, userId);

        // 시작 시간이 종료 시간보다 같거나 늦으면 예외 발생
        validateTimeRange(request.getStartTime(), request.getEndTime());

        // 동일 날짜 스케줄 중복 등록 방지
        if (guideScheduleRepository.findByGuideProfile_IdAndAvailableDate(guideId, request.getAvailableDate()).isPresent()) {
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
        return guideScheduleRepository.findByGuideProfile_IdOrderByAvailableDateAsc(guideId).stream()
                .map(GuideScheduleResponse::from)
                .toList();
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

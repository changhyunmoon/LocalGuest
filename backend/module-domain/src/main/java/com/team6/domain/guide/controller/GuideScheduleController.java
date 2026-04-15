package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideScheduleRequest;
import com.team6.domain.guide.dto.request.SubmitGuideScheduleFormRequest;
import com.team6.domain.guide.dto.request.UpdateGuideScheduleRequest;
import com.team6.domain.guide.dto.response.GuideScheduleFormResponse;
import com.team6.domain.guide.dto.response.GuideScheduleResponse;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.service.GuideScheduleService;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 가이드 스케줄 컨트롤러 (F06-04)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/guides/{guideId}/schedules")
public class GuideScheduleController {

    private final GuideScheduleService guideScheduleService;
    private final MemberRepository memberRepository;

    // 스케줄 등록 (F06-04)
    @PostMapping
    public ResponseEntity<GuideScheduleResponse> addSchedule(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideScheduleRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guideScheduleService.addSchedule(guideId, request, userId));
    }

    // 스케줄 목록 조회 — 날짜 오름차순 (F06-04)
    @GetMapping
    public ResponseEntity<List<GuideScheduleResponse>> getSchedules(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideScheduleService.getSchedules(guideId));
    }

    // 스케줄 수정 (F06-04)
    @PutMapping("/{scheduleId}")
    public ResponseEntity<GuideScheduleResponse> updateSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestBody @Valid UpdateGuideScheduleRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideScheduleService.updateSchedule(scheduleId, guideId, request, userId));
    }

    // 스케줄 삭제 (F06-04)
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        Long userId = getCurrentUserId();
        guideScheduleService.deleteSchedule(scheduleId, guideId, userId);
        return ResponseEntity.noContent().build();
    }

    // PENDING 상태 스케줄 목록 조회 — 수락/거절 대기 중인 스케줄 확인 (F06-04)
    @GetMapping("/pending")
    public ResponseEntity<List<GuideScheduleResponse>> getPendingSchedules(
            @PathVariable Long guideId
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideScheduleService.getPendingSchedules(guideId, userId));
    }

    // 스케줄 수락 — PENDING → BOOKED, 수락 양식 반환 (F06-04)
    @PostMapping("/{scheduleId}/accept")
    public ResponseEntity<GuideScheduleFormResponse> acceptSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideScheduleService.acceptSchedule(scheduleId, guideId, userId));
    }

    // 스케줄 거절 — PENDING → AVAILABLE 복구 (F06-04)
    @PostMapping("/{scheduleId}/reject")
    public ResponseEntity<GuideScheduleResponse> rejectSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideScheduleService.rejectSchedule(scheduleId, guideId, userId));
    }

    // 스케줄 양식 조회 — isPaid=false면 courseDetail null 마스킹, true면 전체 공개 (F06-04)
    // 가이드·게스트 모두 호출 가능
    // TODO: 추후 보안 강화 필요
    // 현재 인증된 사용자라면 누구나 호출 가능
    // 개선 시 가이드 본인 또는 매칭된 게스트만 허용하도록 변경 필요
    @GetMapping("/{scheduleId}/form")
    public ResponseEntity<GuideScheduleFormResponse> getScheduleForm(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(guideScheduleService.getScheduleForm(scheduleId, guideId));
    }

    // 수락 후 여행 계획 양식 저장 — BOOKED 상태에만 가능 (F06-04)
    @PutMapping("/{scheduleId}/form")
    public ResponseEntity<GuideScheduleFormResponse> submitForm(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestBody @Valid SubmitGuideScheduleFormRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideScheduleService.submitForm(scheduleId, guideId, request, userId));
    }

    // [matching 연동] AVAILABLE → PENDING 전환 — matching 도메인이 매칭 요청 시 호출 (F03-04)
    @PatchMapping("/{scheduleId}/pending")
    public ResponseEntity<GuideScheduleResponse> markAsPending(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestParam Long matchRequestId
    ) {
        return ResponseEntity.ok(guideScheduleService.markAsPending(scheduleId, guideId, matchRequestId));
    }

    // [matching 연동] PENDING → BOOKED 전환 — matching 도메인이 최종 확정 시 호출 (F03-05)
    @PatchMapping("/{scheduleId}/book")
    public ResponseEntity<GuideScheduleResponse> markAsBooked(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(guideScheduleService.markAsBooked(scheduleId, guideId));
    }

    // [matching 연동] BOOKED 스케줄 결제 확정 — isPaid=true, courseDetail 잠금 해제 (가이드 수락 후 결제 흐름)
    @PatchMapping("/{scheduleId}/paid-confirm")
    public ResponseEntity<GuideScheduleResponse> markAsPaid(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(guideScheduleService.markAsPaid(scheduleId, guideId));
    }

    // [matching 연동] PENDING·BOOKED → AVAILABLE — 매칭 거절/취소 시 matching 도메인이 호출 (F03/F05)
    @PatchMapping("/{scheduleId}/cancel")
    public ResponseEntity<GuideScheduleResponse> cancelToAvailable(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(guideScheduleService.cancelToAvailable(scheduleId, guideId));
    }

    // 스케줄 상태 변경 — AVAILABLE ↔ BLOCKED (F06-04)
    @PatchMapping("/{scheduleId}/status")
    public ResponseEntity<GuideScheduleResponse> changeStatus(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestParam GuideScheduleStatus status
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(guideScheduleService.changeStatus(scheduleId, guideId, status, userId));
    }

    // JWT에서 현재 로그인 사용자의 memberId 추출
    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserEmail();
        return memberRepository.findByEmail(email)
                .map(Member::getId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.MEMBER_NOT_FOUND));
    }
}

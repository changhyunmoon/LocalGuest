package com.team6.domain.guide.controller;

import com.team6.domain.guide.dto.request.CreateGuideScheduleRequest;
import com.team6.domain.guide.dto.request.UpdateGuideScheduleRequest;
import com.team6.domain.guide.dto.response.GuideScheduleFormResponse;
import com.team6.domain.guide.dto.response.GuideScheduleResponse;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.service.GuideScheduleService;
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

    // 스케줄 등록 (F06-04)
    @PostMapping
    public ResponseEntity<GuideScheduleResponse> addSchedule(
            @PathVariable Long guideId,
            @RequestBody @Valid CreateGuideScheduleRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
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
            @RequestBody @Valid UpdateGuideScheduleRequest request,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideScheduleService.updateSchedule(scheduleId, guideId, request, userId));
    }

    // 스케줄 삭제 (F06-04)
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        guideScheduleService.deleteSchedule(scheduleId, guideId, userId);
        return ResponseEntity.noContent().build();
    }

    // PENDING 상태 스케줄 목록 조회 — 수락/거절 대기 중인 스케줄 확인 (F06-04)
    @GetMapping("/pending")
    public ResponseEntity<List<GuideScheduleResponse>> getPendingSchedules(
            @PathVariable Long guideId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideScheduleService.getPendingSchedules(guideId, userId));
    }

    // 스케줄 수락 — PENDING → BOOKED, 수락 양식 반환 (F06-04)
    @PostMapping("/{scheduleId}/accept")
    public ResponseEntity<GuideScheduleFormResponse> acceptSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideScheduleService.acceptSchedule(scheduleId, guideId, userId));
    }

    // 스케줄 거절 — PENDING → AVAILABLE 복구 (F06-04)
    @PostMapping("/{scheduleId}/reject")
    public ResponseEntity<GuideScheduleResponse> rejectSchedule(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId,
            @RequestHeader("X-User-Id") Long userId // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideScheduleService.rejectSchedule(scheduleId, guideId, userId));
    }

    // [matching 연동] AVAILABLE → PENDING 전환 — matching 도메인이 매칭 요청 시 호출 (F03-04)
    @PatchMapping("/{scheduleId}/pending")
    public ResponseEntity<GuideScheduleResponse> markAsPending(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(guideScheduleService.markAsPending(scheduleId, guideId));
    }

    // [matching 연동] PENDING → BOOKED 전환 — matching 도메인이 최종 확정 시 호출 (F03-05)
    @PatchMapping("/{scheduleId}/book")
    public ResponseEntity<GuideScheduleResponse> markAsBooked(
            @PathVariable Long guideId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(guideScheduleService.markAsBooked(scheduleId, guideId));
    }

    // [matching 연동] BOOKED → AVAILABLE 복구 — matching 도메인이 취소 시 호출 (F05-01/02)
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
            @RequestParam GuideScheduleStatus status, // AVAILABLE / BLOCKED
            @RequestHeader("X-User-Id") Long userId   // JWT 연동 전 임시 헤더
    ) {
        return ResponseEntity.ok(guideScheduleService.changeStatus(scheduleId, guideId, status, userId));
    }
}

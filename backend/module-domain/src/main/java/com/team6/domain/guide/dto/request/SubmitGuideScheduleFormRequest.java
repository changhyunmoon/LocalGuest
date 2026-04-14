package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가이드 스케줄 수락 후 여행 계획 양식 작성 요청 DTO (F06-04)
 * BOOKED 상태 스케줄에만 작성 가능
 */
@Getter
@NoArgsConstructor
public class SubmitGuideScheduleFormRequest {

    @NotBlank(message = "만남 장소를 입력해주세요")
    @Size(max = 200, message = "만남 장소는 200자 이하로 입력해주세요")
    private String meetingPoint;  // 만남 장소

    @NotBlank(message = "안내 메시지를 입력해주세요")
    private String guideMessage;  // 가이드 안내 메시지

    @NotBlank(message = "코스 상세 정보를 입력해주세요")
    private String courseDetail;  // 코스 상세 — 결제 완료 전까지 잠금 처리
}

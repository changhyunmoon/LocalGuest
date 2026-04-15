package com.team6.module.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "PromptRecommendApiRequest", description = "프롬프트 기반 추천 API 요청")
@Getter
@Setter
@NoArgsConstructor
public class PromptRecommendApiRequest {
    @Schema(description = "자연어 여행 요청(지역·스타일·활동 등 파싱)", requiredMode = Schema.RequiredMode.REQUIRED, example = "제주 2박3일 바다 카페")
    private String prompt;
    @Schema(description = "추천할 상위 N명(미입력 시 서버 기본값)", example = "3")
    private Integer topN;
    @Schema(description = "가이드 후보(비우면 서버가 DB 등에서 후보를 채움)")
    private List<GuideRecommendRequest.GuideCandidateDto> guideCandidates;

    @Schema(
            description = "희망 투어일(로컬 날짜, ISO-8601 yyyy-MM-dd). "
                    + "지정 시 해당 날짜에 결제 완료(BOOKED + isPaid) 스케줄이 있는 가이드는 추천 후보에서 제외",
            example = "2026-04-28"
    )
    private LocalDate desiredTourDate;
}

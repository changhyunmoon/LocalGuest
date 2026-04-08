package com.team6.module.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(name = "PromptRecommendApiRequest", description = "프롬프트 기반 추천 API 요청")
@Getter
@NoArgsConstructor
public class PromptRecommendApiRequest {
    @Schema(description = "자연어 여행 요청(지역·스타일·활동 등 파싱)", requiredMode = Schema.RequiredMode.REQUIRED, example = "제주 2박3일 바다 카페")
    private String prompt;
    @Schema(description = "추천할 상위 N명(미입력 시 서버 기본값)", example = "3")
    private Integer topN;
    @Schema(description = "가이드 후보(비우면 서버가 DB 등에서 후보를 채움)")
    private List<GuideRecommendRequest.GuideCandidateDto> guideCandidates;
}

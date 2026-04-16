package com.team6.module.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(name = "AiRecommendClickRequest", description = "추천 카드 클릭 이벤트(관심/탐색 신호)")
@Getter
@Setter
public class AiRecommendClickRequest {

    @Schema(description = "추천에 사용된 policyVersion(응답 헤더/바디와 동일)", example = "2026.04.29")
    private String policyVersion;

    @Schema(description = "클릭된 가이드 ID", example = "12")
    private Long guideId;

    @Schema(description = "추천 리스트 내 노출 순서(1부터)", example = "1")
    private Integer rank;

    @Schema(description = "추천 요청 프롬프트 원문(선택). 민감정보 우려 시 미전달 권장", example = "제주 힐링 여행")
    private String prompt;

    @Schema(description = "프론트가 만든 세션/요청 상관 ID(선택)", example = "web-req-9f12")
    private String clientRequestId;
}


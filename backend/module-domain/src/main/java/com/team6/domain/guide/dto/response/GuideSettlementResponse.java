package com.team6.domain.guide.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 가이드 정산 예정 금액 응답 DTO (F06-05)
 * TODO: matching 도메인 Payment 확정 후 실제 정산 로직으로 교체
 */
@Getter
@Builder
public class GuideSettlementResponse {

    private Long guideId;              // 가이드 프로필 ID
    private BigDecimal expectedAmount; // 정산 예정 금액 — payment.status=COMPLETED 금액 합(수수료 공제 전)
    private String description;        // 안내 메시지
}

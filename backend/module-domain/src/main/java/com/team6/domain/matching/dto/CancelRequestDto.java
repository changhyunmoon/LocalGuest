package com.team6.domain.matching.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelRequestDto {

    private Long requestId;     // 매칭 요청 ID
    private String cancelReason; // 취소 사유
}
package com.team6.domain.matching.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class TourExtensionRequestDto {

    private Long requestId;          // 매칭 요청 ID
    private LocalDate extendedDate;  // 연장 날짜
}
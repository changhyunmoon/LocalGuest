package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TourExtensionSelectRequest {

    @NotNull(message = "연장 선택 여부는 필수입니다")
    private Boolean extend; // true: 하루 연장 선택, false: 미연장 선택
}

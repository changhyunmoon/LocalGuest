package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 날짜 단위 BLOCKED 등록/해제 요청 DTO
 */
@Getter
@NoArgsConstructor
public class BlockDateRequest {

    @NotNull
    private LocalDate date;
}

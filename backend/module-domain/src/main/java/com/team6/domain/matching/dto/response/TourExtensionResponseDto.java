package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.TourExtension;
import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class TourExtensionResponseDto {

    private Long extensionId;            // 연장 신청 ID
    private Long requestId;              // 매칭 요청 ID
    private LocalDate extendedDate;      // 연장 날짜
    private Integer extendedPrice;       // 연장 요금
    private TourExtensionStatus status;  // 연장 상태
    private LocalDateTime deadlineAt;    // 선택 마감일시

    // Entity → DTO 변환
    public static TourExtensionResponseDto from(TourExtension extension) {
        return TourExtensionResponseDto.builder()
                .extensionId(extension.getId())
                .requestId(extension.getMatchRequest().getId())
                .extendedDate(extension.getExtendedDate())
                .extendedPrice(extension.getExtendedPrice())
                .status(extension.getStatus())
                .deadlineAt(extension.getDeadlineAt())
                .build();
    }
}
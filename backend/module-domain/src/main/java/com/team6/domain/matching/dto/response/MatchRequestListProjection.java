package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.enums.MatchRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목록 조회 성능 최적화를 위한 read-only projection.
 * 필요한 컬럼만 선택해 엔티티 전체 로딩 비용을 줄인다.
 */
public interface MatchRequestListProjection {
    Long getRequestId();
    Long getGuestId();
    Long getGuideId();
    Long getGuideScheduleId();
    String getDestination();
    String getConcept();
    String getConceptSummary();
    LocalDate getDesiredDate();
    Integer getDesiredBudget();
    Integer getBudgetMinWon();
    Integer getBudgetMaxWon();
    String getProposedSchedule();
    String getProposeMessage();
    MatchRequestStatus getStatus();
    LocalDateTime getCreatedAt();
}

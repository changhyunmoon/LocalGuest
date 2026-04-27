package com.team6.module.ai.support;

import java.time.LocalDate;
import java.util.List;

/**
 * 가이드의 날짜 가용 정보를 제공한다.
 * <p>
 * module-ai는 도메인(JPA)에 의존하지 않으므로, 실제 구현은 integration/상위 모듈에서 제공할 수 있다.
 */
public interface GuideAvailabilityProvider {

    /**
     * 요청 기간 내에서 가이드가 예약 가능한 날짜 목록(로컬 날짜)을 반환한다.
     * 구현이 없거나 계산 불가 시 빈 리스트를 반환할 수 있다.
     */
    List<LocalDate> availableDates(Long guideId, LocalDate from, LocalDate to);
}


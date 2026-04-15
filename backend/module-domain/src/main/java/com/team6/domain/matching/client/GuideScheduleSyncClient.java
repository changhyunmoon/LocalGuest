package com.team6.domain.matching.client;

public interface GuideScheduleSyncClient {

    /**
     * 스케줄 AVAILABLE → PENDING 및 match_request_id 연결. 가이드 API는 matchRequestId 쿼리 파라미터를 요구한다.
     */
    void markPending(Long guideId, Long scheduleId, Long matchRequestId, Long actorMemberId);

    void cancelToAvailable(Long guideId, Long scheduleId, Long actorMemberId);

    /**
     * 결제 승인 후 가이드 스케줄이 이미 BOOKED인 경우 isPaid=true (paid-confirm).
     * 가이드가 acceptSchedule로 PENDING→BOOKED 한 뒤 게스트가 결제하는 흐름에 맞춘다.
     */
    void confirmPaid(Long guideId, Long scheduleId, Long actorMemberId);
}

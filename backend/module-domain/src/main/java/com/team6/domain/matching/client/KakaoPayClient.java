package com.team6.domain.matching.client;

public interface KakaoPayClient {

    ReadyResult ready(String partnerOrderId, String partnerUserId, String itemName, int totalAmount);

    ApproveResult approve(String tid, String partnerOrderId, String partnerUserId, String pgToken);

    record ReadyResult(String tid, String redirectUrl) {}

    record ApproveResult(String aid) {}
}

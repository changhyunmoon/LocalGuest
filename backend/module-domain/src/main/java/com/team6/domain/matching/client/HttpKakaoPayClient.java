package com.team6.domain.matching.client;

import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class HttpKakaoPayClient implements KakaoPayClient {

    @Value("${kakaopay.base-url:https://open-api.kakaopay.com}")
    private String baseUrl;

    @Value("${kakaopay.secret-key:}")
    private String secretKey;

    @Value("${kakaopay.cid:TC0ONETIME}")
    private String cid;

    @Value("${kakaopay.approval-url:http://localhost:8080/api/matching/payments/kakao/success}")
    private String approvalUrl;

    @Value("${kakaopay.cancel-url:http://localhost:8080/api/matching/payments/kakao/cancel}")
    private String cancelUrl;

    @Value("${kakaopay.fail-url:http://localhost:8080/api/matching/payments/kakao/fail}")
    private String failUrl;

    @Override
    public ReadyResult ready(String partnerOrderId, String partnerUserId, String itemName, int totalAmount) {
        validateSecretKey();

        Map<String, Object> body = Map.of(
                "cid", cid,
                "partner_order_id", partnerOrderId,
                "partner_user_id", partnerUserId,
                "item_name", itemName,
                "quantity", 1,
                "total_amount", totalAmount,
                "tax_free_amount", 0,
                "approval_url", approvalUrl,
                "cancel_url", cancelUrl,
                "fail_url", failUrl
        );

        try {
            Map<?, ?> response = RestClient.create().post()
                    .uri(baseUrl + "/online/v1/payment/ready")
                    .header(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String tid = getRequiredString(response, "tid");
            String redirectUrl = getRequiredString(response, "next_redirect_pc_url");
            return new ReadyResult(tid, redirectUrl);
        } catch (RestClientResponseException e) {
            log.error("[KakaoPay] ready 호출 실패 - status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
        } catch (Exception e) {
            log.error("[KakaoPay] ready 호출 실패 - partnerOrderId={}", partnerOrderId, e);
            throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
        }
    }

    @Override
    public ApproveResult approve(String tid, String partnerOrderId, String partnerUserId, String pgToken) {
        validateSecretKey();

        Map<String, Object> body = Map.of(
                "cid", cid,
                "tid", tid,
                "partner_order_id", partnerOrderId,
                "partner_user_id", partnerUserId,
                "pg_token", pgToken
        );

        try {
            Map<?, ?> response = RestClient.create().post()
                    .uri(baseUrl + "/online/v1/payment/approve")
                    .header(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String aid = getRequiredString(response, "aid");
            return new ApproveResult(aid);
        } catch (RestClientResponseException e) {
            log.error("[KakaoPay] approve 호출 실패 - status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
        } catch (Exception e) {
            log.error("[KakaoPay] approve 호출 실패 - tid={}, partnerOrderId={}", tid, partnerOrderId, e);
            throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
        }
    }

    private void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }
    }

    private String getRequiredString(Map<?, ?> response, String key) {
        Object value = response == null ? null : response.get(key);
        if (Objects.toString(value, "").isBlank()) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
        }
        return Objects.toString(value);
    }
}

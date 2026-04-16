package com.team6.domain.matching.controller;

import com.team6.domain.matching.entity.Payment;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * KakaoPay redirects the browser to our approval/cancel/fail URLs.
 * Those redirects do not include Authorization headers, so this controller must be publicly accessible.
 * It redirects back to the frontend with enough context to continue the confirm step.
 */
@RestController
@RequestMapping("/matching/payments/kakao")
@RequiredArgsConstructor
public class KakaoPayRedirectController {

    private final PaymentRepository paymentRepository;

    @Value("${frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @GetMapping("/success")
    public ResponseEntity<Void> success(
            @RequestParam("pg_token") String pgToken,
            @RequestParam(value = "pgOrderNo", required = false) String pgOrderNo
    ) {
        if (pgOrderNo == null || pgOrderNo.isBlank()) {
            URI redirect = UriComponentsBuilder
                    .fromHttpUrl(frontendBaseUrl)
                    .path("/pay/kakao-stub")
                    .queryParam("result", "success")
                    .queryParam("error", "missing_pgOrderNo")
                    .build(true)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(redirect);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }

        Payment payment = paymentRepository.findByPgOrderNo(pgOrderNo)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));

        URI redirect = buildFrontendRedirect(payment, pgToken, "success");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirect);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> cancel(@RequestParam(value = "pgOrderNo", required = false) String pgOrderNo) {
        if (pgOrderNo == null || pgOrderNo.isBlank()) {
            URI redirect = UriComponentsBuilder
                    .fromHttpUrl(frontendBaseUrl)
                    .path("/pay/kakao-stub")
                    .queryParam("result", "cancel")
                    .queryParam("error", "missing_pgOrderNo")
                    .build(true)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(redirect);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
        Payment payment = paymentRepository.findByPgOrderNo(pgOrderNo)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));
        URI redirect = buildFrontendRedirect(payment, null, "cancel");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirect);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/fail")
    public ResponseEntity<Void> fail(@RequestParam(value = "pgOrderNo", required = false) String pgOrderNo) {
        if (pgOrderNo == null || pgOrderNo.isBlank()) {
            URI redirect = UriComponentsBuilder
                    .fromHttpUrl(frontendBaseUrl)
                    .path("/pay/kakao-stub")
                    .queryParam("result", "fail")
                    .queryParam("error", "missing_pgOrderNo")
                    .build(true)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(redirect);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
        Payment payment = paymentRepository.findByPgOrderNo(pgOrderNo)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));
        URI redirect = buildFrontendRedirect(payment, null, "fail");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirect);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private URI buildFrontendRedirect(Payment payment, String pgToken, String result) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(frontendBaseUrl)
                .path("/pay/kakao-stub")
                .queryParam("paymentId", payment.getId())
                .queryParam("amount", payment.getAmount())
                .queryParam("pgOrderNo", payment.getPgOrderNo())
                .queryParam("paymentType", payment.getPaymentType())
                .queryParam("requestId", payment.getMatchRequest().getId())
                .queryParam("guideId", payment.getMatchRequest().getGuideId())
                .queryParam("result", result);
        if (pgToken != null && !pgToken.isBlank()) {
            b.queryParam("pgToken", pgToken);
        }
        return b.build(true).toUri();
    }
}


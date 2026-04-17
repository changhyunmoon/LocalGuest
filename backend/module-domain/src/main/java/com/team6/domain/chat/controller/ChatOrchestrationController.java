package com.team6.domain.chat.controller;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;

/**
 * 채팅 모듈은 채팅만 담당하고, 도메인 식별자(guideId 등) → 채팅 생성 입력값(email 등) 해석은 domain에서 오케스트레이션한다.
 *
 * <p>module-domain은 module-chat에 의존하지 않으므로, 같은 서버 내 채팅 REST를 HTTP로 호출하는 얇은 게이트웨이로 구현한다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/matching/chat/rooms")
public class ChatOrchestrationController {

    private final GuideProfileRepository guideProfileRepository;
    private final MemberRepository memberRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 게스트가 특정 가이드(guide_profiles PK)와 1:1 DM 방을 조회/생성한다.
     * title 은 비식별 키로 고정한다.
     */
    @PostMapping("/for-guide/{guideId}")
    public ResponseEntity<Map<String, Object>> getOrCreateDmRoomForGuide(
            @PathVariable Long guideId,
            HttpServletRequest request
    ) {
        if (!"ROLE_GUEST".equals(SecurityUtil.getCurrentUserRoleString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "게스트만 채팅방을 생성할 수 있습니다.");
        }

        String ownerEmail = SecurityUtil.getCurrentUserEmail();
        String guideEmail = resolveGuideEmail(guideId);
        if (ownerEmail.equalsIgnoreCase(guideEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인과의 채팅방은 만들 수 없습니다.");
        }

        String title = dmTitleForGuide(guideId);
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization 헤더가 없습니다.");
        }

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        Map<String, Object> existing = findRoomByTitle(base, auth, title);
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }

        Map<String, Object> created = createRoom(base, auth, title, List.of(guideEmail));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private Map<String, Object> findRoomByTitle(String base, String auth, String title) {
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                base + "/chat/rooms",
                HttpMethod.GET,
                new HttpEntity<>(headers(auth)),
                new ParameterizedTypeReference<>() {
                }
        );
        Object roomsObj = res.getBody() != null ? res.getBody().get("rooms") : null;
        if (!(roomsObj instanceof List<?> rooms)) return null;
        for (Object o : rooms) {
            if (o instanceof Map<?, ?> m) {
                Object t = m.get("title");
                if (title.equals(t)) {
                    //noinspection unchecked
                    return (Map<String, Object>) m;
                }
            }
        }
        return null;
    }

    private Map<String, Object> createRoom(String base, String auth, String title, List<String> participantEmails) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("participantEmails", participantEmails);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                base + "/chat/rooms",
                HttpMethod.POST,
                new HttpEntity<>(body, headers(auth)),
                new ParameterizedTypeReference<>() {
                }
        );
        Map<String, Object> data = res.getBody();
        if (data == null || data.get("roomId") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "채팅방 생성 응답이 올바르지 않습니다.");
        }
        return data;
    }

    private HttpHeaders headers(String auth) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, auth);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String resolveGuideEmail(Long guideId) {
        Long memberId = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가이드를 찾을 수 없습니다."))
                .getMemberId();
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가이드 계정을 찾을 수 없습니다."))
                .getEmail();
    }

    private static String dmTitleForGuide(Long guideId) {
        return "LG-DM-GUIDE-" + guideId;
    }
}


package com.team6.domain.chat.controller;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.member.entity.Role;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern DM_GUIDE_TITLE = Pattern.compile("^LG-DM-GUIDE-(\\d+)$");

    /**
     * 채팅방 목록 조회(표시용): title을 카톡처럼 상대 닉네임으로 가공한다.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRoomListView(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization 헤더가 없습니다.");
        }

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        Map<String, Object> raw = fetchRooms(base, auth);
        Object roomsObj = raw.get("rooms");
        if (!(roomsObj instanceof List<?> rooms)) {
            return ResponseEntity.ok(raw);
        }

        String role = SecurityUtil.getCurrentUserRoleString();
        List<Object> mapped = new ArrayList<>(rooms.size());
        for (Object o : rooms) {
            if (!(o instanceof Map<?, ?> m)) {
                mapped.add(o);
                continue;
            }
            //noinspection unchecked
            Map<String, Object> room = new LinkedHashMap<>((Map<String, Object>) m);
            String title = asString(room.get("title"));
            String display = resolveDisplayTitle(role, title, room);
            if (display != null && !display.isBlank()) {
                room.put("title", display);
            }
            mapped.add(room);
        }

        Map<String, Object> out = new LinkedHashMap<>(raw);
        out.put("rooms", mapped);
        return ResponseEntity.ok(out);
    }

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

    private Map<String, Object> fetchRooms(String base, String auth) {
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                base + "/chat/rooms",
                HttpMethod.GET,
                new HttpEntity<>(headers(auth)),
                new ParameterizedTypeReference<>() {
                }
        );
        return res.getBody() != null ? res.getBody() : Map.of("rooms", List.of(), "totalCount", 0);
    }

    private Map<String, Object> findRoomByTitle(String base, String auth, String title) {
        Map<String, Object> raw = fetchRooms(base, auth);
        Object roomsObj = raw.get("rooms");
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

    private String resolveDisplayTitle(String role, String rawTitle, Map<String, Object> room) {
        if (rawTitle == null) return null;

        Matcher dm = DM_GUIDE_TITLE.matcher(rawTitle);
        if (dm.matches()) {
            Long guideId = Long.valueOf(dm.group(1));
            if ("ROLE_GUEST".equals(role)) {
                return guideProfileRepository.findById(guideId).map(g -> g.getNickname()).orElse(rawTitle);
            }
            if ("ROLE_GUIDE".equals(role)) {
                String ownerEmail = asString(room.get("ownerEmail"));
                if (ownerEmail == null || ownerEmail.isBlank()) return rawTitle;
                return memberRepository.findByEmailAndRole(ownerEmail, Role.GUEST)
                        .map(m -> m.getNickname() != null && !m.getNickname().isBlank() ? m.getNickname() : ownerEmail.split("@")[0])
                        .orElse(ownerEmail.split("@")[0]);
            }
        }
        return rawTitle;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String dmTitleForGuide(Long guideId) {
        return "LG-DM-GUIDE-" + guideId;
    }
}


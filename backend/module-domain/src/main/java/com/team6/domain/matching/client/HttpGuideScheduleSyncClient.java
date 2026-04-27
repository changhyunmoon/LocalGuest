package com.team6.domain.matching.client;

import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpGuideScheduleSyncClient implements GuideScheduleSyncClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Value("${matching.guide-sync.base-url:http://localhost:8080}")
    private String guideSyncBaseUrl;

    @Value("${matching.guide-sync.context-path:${server.servlet.context-path:/api}}")
    private String guideSyncContextPath;

    @Override
    public void markPending(Long guideId, Long scheduleId, Long matchRequestId, Long actorMemberId) {
        patch(guideId, scheduleId, "/pending", actorMemberId, matchRequestId);
    }

    @Override
    public void cancelToAvailable(Long guideId, Long scheduleId, Long actorMemberId) {
        patch(guideId, scheduleId, "/cancel", actorMemberId, null);
    }

    @Override
    public void confirmPaid(Long guideId, Long scheduleId, Long actorMemberId) {
        patch(guideId, scheduleId, "/paid-confirm", actorMemberId, null);
    }

    private void patch(Long guideId, Long scheduleId, String actionPath, Long actorMemberId, Long matchRequestId) {
        String uri = buildSyncUri(guideId, scheduleId, actionPath, matchRequestId);
        try {
            RestClient.RequestBodySpec req = RestClient.create().patch()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");

            if (actorMemberId != null) {
                req.header(USER_ID_HEADER, String.valueOf(actorMemberId));
            }

            String authHeader = resolveAuthorizationHeader();
            if (authHeader != null && !authHeader.isBlank()) {
                req.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            req.retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("[GuideSync] 스케줄 동기화 HTTP 실패 — status={}, uri={}, responseBody={}, guideId={}, scheduleId={}, action={}, actorId={}",
                    e.getStatusCode().value(),
                    uri,
                    abbreviate(e.getResponseBodyAsString()),
                    guideId,
                    scheduleId,
                    actionPath,
                    actorMemberId,
                    e);
            throw new MatchingException(MatchingErrorCode.GUIDE_SCHEDULE_SYNC_FAILED);
        } catch (Exception e) {
            log.error("[GuideSync] 스케줄 동기화 실패 — uri={}, guideId={}, scheduleId={}, action={}, actorId={}",
                    uri, guideId, scheduleId, actionPath, actorMemberId, e);
            throw new MatchingException(MatchingErrorCode.GUIDE_SCHEDULE_SYNC_FAILED);
        }
    }

    private String buildSyncUri(Long guideId, Long scheduleId, String actionPath, Long matchRequestId) {
        String base = trimTrailingSlash(guideSyncBaseUrl);
        String contextPath = normalizeContextPath(guideSyncContextPath);

        if (!contextPath.isBlank() && base.endsWith(contextPath)) {
            contextPath = "";
        }

        String uri = base + contextPath + "/guides/" + guideId + "/schedules/" + scheduleId + actionPath;
        if (matchRequestId != null) {
            uri = uri + "?matchRequestId=" + matchRequestId;
        }
        return uri;
    }

    private String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath.trim())) {
            return "";
        }
        String normalized = contextPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return trimTrailingSlash(normalized);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        int maxLength = 500;
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }

    private String resolveAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}

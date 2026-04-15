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

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpGuideScheduleSyncClient implements GuideScheduleSyncClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Value("${matching.guide-sync.base-url:http://localhost:8080/api}")
    private String guideSyncBaseUrl;

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
        try {
            String uri = guideSyncBaseUrl + "/guides/" + guideId + "/schedules/" + scheduleId + actionPath;
            if (matchRequestId != null) {
                uri = uri + "?matchRequestId=" + matchRequestId;
            }
            RestClient.RequestBodySpec req = RestClient.create().patch()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(USER_ID_HEADER, String.valueOf(actorMemberId));

            String authHeader = resolveAuthorizationHeader();
            if (authHeader != null && !authHeader.isBlank()) {
                req.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            req.retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("[GuideSync] 스케줄 동기화 실패 — guideId={}, scheduleId={}, action={}, actorId={}",
                    guideId, scheduleId, actionPath, actorMemberId, e);
            throw new MatchingException(MatchingErrorCode.GUIDE_SCHEDULE_SYNC_FAILED);
        }
    }

    private String resolveAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}

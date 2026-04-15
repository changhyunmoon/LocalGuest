package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class RequestGuideCandidateProvider implements com.team6.module.ai.support.GuideCandidateProvider {

    @Override
    public List<GuideRecommendRequest.GuideCandidateDto> getCandidates(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            LocalDate desiredTourDate
    ) {
        // DB 없음: 결제 완료 일정 제외는 integration 구현(DbBacked)에서 처리한다.
        return candidates == null ? List.of() : candidates;
    }
}
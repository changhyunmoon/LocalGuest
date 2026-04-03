package com.team6.module.ai.engine;

import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchingEngine {

    private final ScoreCalculator scoreCalculator;
    private final ReasonGenerator reasonGenerator;

    public GuideRecommendResponse recommend(
            TravelerPreference preference,
            List<GuideAiProfile> guides,
            int topN
    ) {
        List<GuideRecommendItem> items = guides.stream()
                .map(guide -> {
                    int score = scoreCalculator.calculate(preference, guide);
                    String reason = reasonGenerator.generate(preference, guide, score);

                    return GuideRecommendItem.builder()
                            .guideId(guide.getGuideId())
                            .guideName(guide.getGuideName())
                            .score(score)
                            .reason(reason)
                            .build();
                })
                .sorted(Comparator.comparingInt(GuideRecommendItem::getScore).reversed())
                .limit(topN)
                .toList();

        return GuideRecommendResponse.builder()
                .totalCount(items.size())
                .recommendations(items)
                .build();
    }
}
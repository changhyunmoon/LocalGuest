package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StyleMatchPolicy {

    private final ScoringPolicySnapshot scoring;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getTravelStyle() == null || guide.getGuideStyle() == null) {
            return 0;
        }
        return pref.getTravelStyle().equalsIgnoreCase(guide.getGuideStyle()) ? scoring.weightStyle() : 0;
    }
}

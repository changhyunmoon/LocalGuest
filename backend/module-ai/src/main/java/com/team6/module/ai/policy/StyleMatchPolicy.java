package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

@Component
public class StyleMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getTravelStyle() == null || guide.getGuideStyle() == null) {
            return 0;
        }
        return pref.getTravelStyle().equalsIgnoreCase(guide.getGuideStyle()) ? ScoreWeight.STYLE : 0;
    }
}